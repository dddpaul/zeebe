/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.util.ResettableJobActivationRequestResponseObserver;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

@WebMvcTest(JobController.class)
public class JobControllerTest extends RestControllerTest {

  static final String JOBS_BASE_URL = "/v2/jobs";

  @Autowired ActivateJobsHandler<JobActivationResponse> activateJobsHandler;
  @Autowired StubbedBrokerClient stubbedBrokerClient;
  @SpyBean ResettableJobActivationRequestResponseObserver responseObserver;

  @BeforeEach
  void setup() {
    responseObserver.reset();
  }

  @Test
  void shouldActivateJobsImmediatelyIfAvailable() throws Exception {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.addAvailableJobs("TEST", 2);
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": ["default"],
          "worker": "bar"
        }""";
    final var expectedBody =
        """
        {
          "jobs": [
            {
              "key": 2251799813685248,
              "type": "TEST",
              "processInstanceKey": 123,
              "processDefinitionKey": 4532,
              "processDefinitionVersion": 23,
              "elementInstanceKey": 459,
              "retries": 12,
              "deadline": 123123123,
              "tenantId": "default",
              "variables": {},
              "customHeaders": {},
              "bpmnProcessId": "stubProcess",
              "elementId": "stubActivity",
              "worker": "bar"
            },
            {
              "key": 2251799813685249,
              "type": "TEST",
              "processInstanceKey": 123,
              "processDefinitionKey": 4532,
              "processDefinitionVersion": 23,
              "elementInstanceKey": 459,
              "retries": 12,
              "deadline": 123123123,
              "tenantId": "default",
              "variables": {},
              "customHeaders": {},
              "bpmnProcessId": "stubProcess",
              "elementId": "stubActivity",
              "worker": "bar"
            }
          ]
        }""";
    // when / then
    webClient
        .perform(
            asyncRequest(
                post(JOBS_BASE_URL + "/activation")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verify(responseObserver, Mockito.times(1)).onNext(any());
    Mockito.verify(responseObserver).onCompleted();
  }

  @Test
  void shouldReturnNoJobsImmediatelyIfNoneAvailable() throws Exception {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 10,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": ["foo"],
          "tenantIds": ["default"],
          "worker": "bar"
        }""";
    final var expectedBody =
        """
        {
          "jobs": []
        }""";
    // when / then
    webClient
        .perform(
            asyncRequest(
                post(JOBS_BASE_URL + "/activation")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verify(responseObserver, Mockito.never()).onNext(any());
    Mockito.verify(responseObserver).onCompleted();
  }

  @Test
  void shouldActivateJobsRoundRobin() throws Exception {
    // given
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(stubbedBrokerClient);

    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "tenantIds": ["default"],
          "worker": "bar"
        }""";

    /*
     * Get the baseline partition since the current one could be any partition.
     * The job activation handler is created once for all tests, so previous tests can have moved
     * the round-robin index by any number already.
     */
    stub.addAvailableJobs("TEST", 1);
    final MvcResult result =
        webClient
            .perform(
                asyncRequest(
                    post(JOBS_BASE_URL + "/activation")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    final int basePartition =
        Protocol.decodePartitionId(
            JsonPath.read(result.getResponse().getContentAsString(), "$.jobs[0].key"));
    final int partitionsCount =
        stubbedBrokerClient.getTopologyManager().getTopology().getPartitionsCount();

    // try activating jobs on each partition round-robin
    for (int partitionOffset = 1; partitionOffset <= partitionsCount; partitionOffset++) {
      // calculate the expected partition ID to build the assertion key for
      final int expectedPartitionId = (basePartition + partitionOffset - 1) % partitionsCount + 1;
      // reset the results and add new jobs that can be fetched
      responseObserver.reset();
      stub.addAvailableJobs("TEST", 2);
      // when/then
      webClient
          .perform(
              asyncRequest(
                  post(JOBS_BASE_URL + "/activation")
                      .accept(MediaType.APPLICATION_JSON)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(request)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(
              jsonPath("$.jobs[0].key").value(Protocol.encodePartitionId(expectedPartitionId, 0)))
          .andExpect(
              jsonPath("$.jobs[1].key").value(Protocol.encodePartitionId(expectedPartitionId, 1)));
    }
  }

  @Test
  void shouldSendRejectionWithoutRetrying() throws Exception {
    // given
    final AtomicInteger callCounter = new AtomicInteger();
    stubbedBrokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        request -> {
          callCounter.incrementAndGet();
          return new BrokerRejectionResponse<>(
              new BrokerRejection(Intent.UNKNOWN, 1, RejectionType.INVALID_ARGUMENT, "expected"));
        });

    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 10,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": ["foo"],
          "tenantIds": ["default"],
          "worker": "bar"
        }""";
    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "Command 'UNKNOWN' rejected with code 'INVALID_ARGUMENT': expected",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/activation");

    // when/then
    webClient
        .perform(
            asyncRequest(
                post(JOBS_BASE_URL + "/activation")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    assertThat(callCounter).hasValue(1);
  }

  @TestConfiguration
  static class TestJobApplication {

    @Bean
    public ActorClock actorClock() {
      return new ControlledActorClock();
    }

    @Bean(destroyMethod = "close")
    public ActorScheduler actorScheduler(final ActorClock clock) {
      final ActorScheduler scheduler =
          ActorScheduler.newActorScheduler()
              .setCpuBoundActorThreadCount(
                  Math.max(1, Runtime.getRuntime().availableProcessors() - 2))
              .setIoBoundActorThreadCount(2)
              .setActorClock(clock)
              .build();
      scheduler.start();
      return scheduler;
    }

    @Bean
    public StubbedBrokerClient brokerClient() {
      return new StubbedBrokerClient();
    }

    @Bean
    public ResettableJobActivationRequestResponseObserver responseObserver() {
      return new ResettableJobActivationRequestResponseObserver(new CompletableFuture<>());
    }

    @Bean
    public ResponseObserverProvider responseObserverProvider(
        final ResettableJobActivationRequestResponseObserver responseObserver) {
      return responseObserver::setResult;
    }

    @Bean
    public ActivateJobsHandler<JobActivationResponse> activateJobsHandler(
        final BrokerClient brokerClient, final ActorScheduler actorScheduler) {
      final var handler =
          new RoundRobinActivateJobsHandler<>(
              brokerClient,
              DataSize.ofMegabytes(4L).toBytes(),
              ResponseMapper::toActivateJobsResponse,
              RuntimeException::new);
      final var future = new CompletableFuture<>();
      final var actor =
          Actor.newActor()
              .name("JobActivationHandler-JobControllerTest")
              .actorStartedHandler(handler.andThen(future::complete))
              .build();
      actorScheduler.submitActor(actor);
      return handler;
    }
  }
}

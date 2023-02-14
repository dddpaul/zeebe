/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;
import org.testcontainers.utility.DockerImageName;

public class ElasticsearchExporterFaultToleranceIT
    extends AbstractElasticsearchExporterIntegrationTestCase {

  // we use Toxiproxy to simulate Elastic being "down" arbitrarily
  private final ToxiproxyContainer proxy =
      new ToxiproxyContainer(DockerImageName.parse("shopify/toxiproxy").withTag("2.1.0"))
          .withNetwork(network)
          .withNetworkAliases("proxy");

  private ContainerProxy elasticProxy;

  @Before
  public void before() {
    proxy.start();
    elasticProxy = proxy.getProxy("elastic", 9200);
  }

  @After
  public void after() {
    proxy.stop();
  }

  @Test
  public void shouldExportEvenIfElasticNotInitiallyReachable() {
    // given
    elastic.withPort(elasticProxy.getProxyPort());
    configuration = getDefaultConfiguration();
    configuration.index.prefix = "zeebe";
    esClient = createElasticsearchClient(configuration);
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);

    // when
    startBrokerWithoutWaitingForIndexTemplates();
    exporterBrokerRule.publishMessage("message", "123");
    elastic.start();
    awaitIndexTemplatesCreation();

    // then
    final var records =
        RecordingExporter.messageRecords()
            .withCorrelationKey("123")
            .withName("message")
            .collect(Collectors.toList()); // collect here because we iterate multiple times
    Awaitility.await()
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(() -> assertThat(records).allMatch(this::wasExported));
    assertIndexSettings();
  }

  private boolean wasExported(final Record<?> record) {
    try {
      return esClient.getDocument(record) != null;
    } catch (final Exception e) {
      // suppress exception in order to retry and see if it was exported yet or not
      // the exception can occur since elastic may not be ready yet, or maybe the index hasn't been
      // created yet, etc.
    }

    return false;
  }
}
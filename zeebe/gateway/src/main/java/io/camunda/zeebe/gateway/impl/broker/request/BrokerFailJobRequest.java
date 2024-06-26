/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.agrona.DirectBuffer;

public final class BrokerFailJobRequest extends BrokerExecuteCommand<JobRecord> {

  private final JobRecord requestDto = new JobRecord();

  public BrokerFailJobRequest(final long key, final int retries, final long retryBackOff) {
    super(ValueType.JOB, JobIntent.FAIL);
    request.setKey(key);
    requestDto.setRetries(retries);
    requestDto.setRetryBackoff(retryBackOff);
  }

  public BrokerFailJobRequest setErrorMessage(final String errorMessage) {
    requestDto.setErrorMessage(errorMessage);
    return this;
  }

  public BrokerFailJobRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  @Override
  public JobRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected JobRecord toResponseDto(final DirectBuffer buffer) {
    final JobRecord responseDto = new JobRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

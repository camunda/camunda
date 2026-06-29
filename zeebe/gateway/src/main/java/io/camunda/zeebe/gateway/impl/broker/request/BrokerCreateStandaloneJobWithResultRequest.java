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

/**
 * Sends a {@link JobIntent#CREATE} command for a standalone job and awaits its result. The created
 * job's completion is correlated back over this request's channel, so the response is a {@link
 * JobRecord} (the completed job, carrying the worker's result variables).
 */
public final class BrokerCreateStandaloneJobWithResultRequest
    extends BrokerExecuteCommand<JobRecord> {

  private final JobRecord requestDto = new JobRecord();

  public BrokerCreateStandaloneJobWithResultRequest() {
    super(ValueType.JOB, JobIntent.CREATE);
  }

  public BrokerCreateStandaloneJobWithResultRequest setType(final String type) {
    requestDto.setType(type);
    return this;
  }

  public BrokerCreateStandaloneJobWithResultRequest setRetries(final int retries) {
    requestDto.setRetries(retries);
    return this;
  }

  public BrokerCreateStandaloneJobWithResultRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerCreateStandaloneJobWithResultRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
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

  @Override
  protected boolean isValidResponse() {
    return response.getValueType() == ValueType.JOB;
  }
}

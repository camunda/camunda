/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateStandaloneJobRequest.StandaloneJobAnswer;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.agrona.DirectBuffer;

/**
 * Creates a standalone job and waits for its answer: the broker responds only once a worker
 * completes, fails or throws an error for the job, or once the job expires.
 */
public final class BrokerCreateStandaloneJobRequest
    extends BrokerExecuteCommand<StandaloneJobAnswer> {

  private final JobRecord requestDto = new JobRecord();

  public BrokerCreateStandaloneJobRequest(final String type) {
    super(ValueType.JOB, JobIntent.CREATE);
    requestDto.setType(type);
  }

  public BrokerCreateStandaloneJobRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  public BrokerCreateStandaloneJobRequest setInputExpression(final String inputExpression) {
    requestDto.setInputExpression(inputExpression);
    return this;
  }

  public BrokerCreateStandaloneJobRequest setCustomHeaders(final DirectBuffer customHeaders) {
    requestDto.setCustomHeaders(customHeaders);
    return this;
  }

  /** The time the job waits for an answer before the broker expires it. */
  public BrokerCreateStandaloneJobRequest setTimeToAnswer(final long timeToAnswerMillis) {
    requestDto.setTimeout(timeToAnswerMillis);
    return this;
  }

  @Override
  public JobRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected StandaloneJobAnswer toResponseDto(final DirectBuffer buffer) {
    final JobRecord job = new JobRecord();
    job.wrap(buffer);
    return new StandaloneJobAnswer(response.getKey(), response.getIntent(), job);
  }

  /**
   * The answer to a standalone job: {@link JobIntent#COMPLETED}, {@link JobIntent#ERROR_THROWN} or
   * {@link JobIntent#FAILED} when a worker answered it, {@link JobIntent#EXPIRED} when none did in
   * time.
   */
  public record StandaloneJobAnswer(long jobKey, Intent outcome, JobRecord job) {}
}

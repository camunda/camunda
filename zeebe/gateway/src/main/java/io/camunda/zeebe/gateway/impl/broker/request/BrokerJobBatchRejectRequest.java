/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import org.agrona.DirectBuffer;

/**
 * Rejects a pending JobBatch delivery when the gateway did not receive a usable activation
 * response. The broker yields still-activated jobs for that attempt.
 */
public final class BrokerJobBatchRejectRequest extends BrokerExecuteCommand<JobBatchRecord> {

  private final JobBatchRecord requestDto = new JobBatchRecord();

  public BrokerJobBatchRejectRequest(
      final int partitionId, final String jobType, final long deliveryAttemptKey) {
    super(ValueType.JOB_BATCH, JobBatchIntent.REJECT);
    setPartitionId(partitionId);
    requestDto.setType(jobType);
    requestDto.setDeliveryAttemptKey(deliveryAttemptKey);
  }

  @Override
  public JobBatchRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected JobBatchRecord toResponseDto(final DirectBuffer buffer) {
    final JobBatchRecord responseDto = new JobBatchRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

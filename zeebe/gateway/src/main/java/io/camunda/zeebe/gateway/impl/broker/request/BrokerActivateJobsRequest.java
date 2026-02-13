/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class BrokerActivateJobsRequest extends BrokerExecuteCommand<JobBatchRecord> {

  private final JobBatchRecord requestDto = new JobBatchRecord();

  public BrokerActivateJobsRequest(final String jobType) {
    super(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE);
    requestDto.setType(jobType);
  }

  public BrokerActivateJobsRequest setWorker(final String worker) {
    requestDto.setWorker(worker);
    return this;
  }

  public BrokerActivateJobsRequest setTimeout(final long timeout) {
    requestDto.setTimeout(timeout);
    return this;
  }

  public BrokerActivateJobsRequest setMaxJobsToActivate(final int maxJobsToActivate) {
    requestDto.setMaxJobsToActivate(maxJobsToActivate);
    return this;
  }

  public BrokerActivateJobsRequest setVariables(final List<String> fetchVariables) {
    final ValueArray<StringValue> variables = requestDto.variables();
    fetchVariables.stream()
        .map(BufferUtil::wrapString)
        .forEach(buffer -> variables.add().wrap(buffer));

    return this;
  }

  public BrokerActivateJobsRequest setTenantIds(final List<String> tenantIds) {
    requestDto.setTenantIds(tenantIds);
    return this;
  }

  public BrokerActivateJobsRequest setTenantFilter(final TenantFilter tenantFilter) {
    requestDto.setTenantFilter(tenantFilter);
    return this;
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

  @Override
  public String toString() {
    return "BrokerActivateJobsRequest{" + "requestDto=" + requestDto + '}';
  }
}

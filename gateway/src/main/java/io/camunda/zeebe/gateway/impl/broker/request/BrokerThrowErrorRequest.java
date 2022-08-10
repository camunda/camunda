/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.agrona.DirectBuffer;

public final class BrokerThrowErrorRequest extends BrokerExecuteCommand<JobRecord> {

  private final JobRecord requestDto = new JobRecord();

  public BrokerThrowErrorRequest(final long key, final String errorCode) {
    this(key, errorCode, RecordValueWithTenant.DEFAULT_TENANT_ID);
  }
  public BrokerThrowErrorRequest(final long key, final String errorCode, final String tenantId) {
    super(ValueType.JOB, JobIntent.THROW_ERROR);
    request.setKey(key);
    requestDto.setErrorCode(wrapString(errorCode));
    requestDto.setTenantId(tenantId);
  }

  public BrokerThrowErrorRequest setErrorMessage(final String errorMessage) {
    requestDto.setErrorMessage(errorMessage);
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

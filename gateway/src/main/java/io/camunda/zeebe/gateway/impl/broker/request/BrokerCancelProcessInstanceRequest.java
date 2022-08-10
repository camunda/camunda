/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;

public class BrokerCancelProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceRecord> {

  private final ProcessInstanceRecord requestDto = new ProcessInstanceRecord();

  public BrokerCancelProcessInstanceRequest() {
    this(RecordValueWithTenant.DEFAULT_TENANT_ID);
  }
  public BrokerCancelProcessInstanceRequest(String tenantId) {
    super(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL);
    requestDto.setTenantId(tenantId);
  }

  public BrokerCancelProcessInstanceRequest setProcessInstanceKey(final long processInstanceKey) {
    request.setKey(processInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceRecord responseDto = new ProcessInstanceRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

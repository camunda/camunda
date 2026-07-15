/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import org.agrona.DirectBuffer;

public final class BrokerAssignProcessInstanceBusinessIdRequest
    extends BrokerExecuteCommand<ProcessInstanceBusinessIdRecord> {

  private final ProcessInstanceBusinessIdRecord requestDto = new ProcessInstanceBusinessIdRecord();

  public BrokerAssignProcessInstanceBusinessIdRequest() {
    super(ValueType.PROCESS_INSTANCE_BUSINESS_ID, ProcessInstanceBusinessIdIntent.ASSIGN);
  }

  public BrokerAssignProcessInstanceBusinessIdRequest setProcessInstanceKey(
      final long processInstanceKey) {
    requestDto.setProcessInstanceKey(processInstanceKey);
    request.setKey(processInstanceKey);
    return this;
  }

  public BrokerAssignProcessInstanceBusinessIdRequest setBusinessId(final String businessId) {
    requestDto.setBusinessId(businessId);
    return this;
  }

  @Override
  public ProcessInstanceBusinessIdRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceBusinessIdRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceBusinessIdRecord responseDto = new ProcessInstanceBusinessIdRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

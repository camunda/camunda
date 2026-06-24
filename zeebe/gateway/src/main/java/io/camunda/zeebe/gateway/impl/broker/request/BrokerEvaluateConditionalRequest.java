/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import org.agrona.DirectBuffer;

public final class BrokerEvaluateConditionalRequest
    extends BrokerExecuteCommand<ConditionalEvaluationRecord> {

  private final ConditionalEvaluationRecord requestDto = new ConditionalEvaluationRecord();

  public BrokerEvaluateConditionalRequest() {
    super(ValueType.CONDITIONAL_EVALUATION, ConditionalEvaluationIntent.EVALUATE);
  }

  public BrokerEvaluateConditionalRequest setProcessDefinitionKey(final long processDefinitionKey) {
    requestDto.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  public BrokerEvaluateConditionalRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerEvaluateConditionalRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  @Override
  public ConditionalEvaluationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ConditionalEvaluationRecord toResponseDto(final DirectBuffer buffer) {
    final ConditionalEvaluationRecord responseDto = new ConditionalEvaluationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerExpressionEvaluationRequest extends BrokerExecuteCommand<ExpressionRecord> {

  private final ExpressionRecord requestDto = new ExpressionRecord();

  public BrokerExpressionEvaluationRequest() {
    super(ValueType.EXPRESSION, ExpressionIntent.EVALUATE);
  }

  public BrokerExpressionEvaluationRequest setExpression(final String expression) {
    requestDto.setExpression(expression);
    return this;
  }

  public BrokerExpressionEvaluationRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ExpressionRecord toResponseDto(final DirectBuffer buffer) {
    final ExpressionRecord responseDto = new ExpressionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

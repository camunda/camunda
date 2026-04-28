/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

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

  /**
   * Routes this request to the partition that owns the given process instance and propagates the
   * key to the engine so the expression evaluates in that instance's variable scope.
   */
  public BrokerExpressionEvaluationRequest setProcessInstanceKey(final Long processInstanceKey) {
    if (processInstanceKey == null) {
      return this;
    }
    requestDto.setProcessInstanceKey(processInstanceKey);
    setPartitionId(Protocol.decodePartitionId(processInstanceKey));
    return this;
  }

  /**
   * Routes this request to the partition that owns the given element instance and propagates the
   * key to the engine so the expression evaluates in that instance's variable scope.
   */
  public BrokerExpressionEvaluationRequest setElementInstanceKey(final Long elementInstanceKey) {
    if (elementInstanceKey == null) {
      return this;
    }
    requestDto.setElementInstanceKey(elementInstanceKey);
    setPartitionId(Protocol.decodePartitionId(elementInstanceKey));
    return this;
  }

  public BrokerExpressionEvaluationRequest setVariables(final Map<String, Object> context) {
    requestDto.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(context)));
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

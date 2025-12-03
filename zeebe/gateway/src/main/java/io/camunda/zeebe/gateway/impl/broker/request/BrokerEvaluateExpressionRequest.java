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
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.value.ExpressionScopeType;
import org.agrona.DirectBuffer;

public final class BrokerEvaluateExpressionRequest
    extends BrokerExecuteCommand<ExpressionRecord> {

  private final ExpressionRecord requestDto = new ExpressionRecord();

  public BrokerEvaluateExpressionRequest(final String expression) {
    super(ValueType.EXPRESSION, ExpressionIntent.EVALUATE);
    requestDto.setExpression(expression);
    requestDto.setScopeType(ExpressionScopeType.NONE);

    // Route to deployment partition (partition 1) for processing
    // This avoids routing based on process instance key
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerEvaluateExpressionRequest setContext(final DirectBuffer context) {
    requestDto.setContext(context);
    return this;
  }

  public BrokerEvaluateExpressionRequest setScopeType(final ExpressionScopeType scopeType) {
    requestDto.setScopeType(scopeType);
    return this;
  }

  public BrokerEvaluateExpressionRequest setProcessInstanceKey(final long processInstanceKey) {
    requestDto.setProcessInstanceKey(processInstanceKey);

    // If we have a process instance key, we might want to route to its partition
    // However, for simplicity and to avoid cross-partition issues, we keep it on partition 1
    // The processor will need to fetch variables from the appropriate partition if needed
    return this;
  }

  public BrokerEvaluateExpressionRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  @Override
  public ExpressionRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ExpressionRecord toResponseDto(final DirectBuffer buffer) {
    final ExpressionRecord responseDto = new ExpressionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}

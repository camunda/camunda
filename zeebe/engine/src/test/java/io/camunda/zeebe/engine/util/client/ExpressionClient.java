/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionScopeType;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;

public final class ExpressionClient {

  private static final Function<Long, Record<ExpressionRecordValue>> SUCCESS_SUPPLIER =
      (position) ->
          RecordingExporter.expressionRecords()
              .withIntent(ExpressionIntent.EVALUATED)
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<ExpressionRecordValue>> REJECTION_SUPPLIER =
      (position) ->
          RecordingExporter.expressionRecords()
              .onlyCommandRejections()
              .withIntent(ExpressionIntent.EVALUATE)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;

  private String expression;
  private ExpressionScopeType scopeType = ExpressionScopeType.NONE;
  private long processInstanceKey = -1;
  private Map<String, Object> context = Map.of();
  private String tenantId = "";
  private Function<Long, Record<ExpressionRecordValue>> expectation = SUCCESS_SUPPLIER;

  public ExpressionClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ExpressionClient withExpression(final String expression) {
    this.expression = expression;
    return this;
  }

  public ExpressionClient withScope(final ExpressionScopeType scopeType) {
    this.scopeType = scopeType;
    return this;
  }

  public ExpressionClient withProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public ExpressionClient withContext(final Map<String, Object> context) {
    this.context = context;
    return this;
  }

  public ExpressionClient withTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ExpressionClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<ExpressionRecordValue> evaluate() {
    final var expressionRecord = new ExpressionRecord();
    expressionRecord.setExpression(expression);
    expressionRecord.setScopeType(scopeType);
    expressionRecord.setProcessInstanceKey(processInstanceKey);
    expressionRecord.setContext(MsgPackUtil.asMsgPack(context));
    expressionRecord.setTenantId(tenantId);

    final long position = writer.writeCommand(ExpressionIntent.EVALUATE, expressionRecord);

    return expectation.apply(position);
  }
}

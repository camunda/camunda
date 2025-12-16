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
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.LongFunction;

public final class ExpressionClient {

  private static final long DEFAULT_KEY = -1;

  private static final LongFunction<Record<ExpressionRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.expressionRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();
  private static final LongFunction<Record<ExpressionRecordValue>> REJECTION_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.expressionRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();

  private final ExpressionRecord expressionRecord;
  private final CommandWriter writer;
  private LongFunction<Record<ExpressionRecordValue>> expectation = SUCCESS_SUPPLIER;

  public ExpressionClient(final CommandWriter writer) {
    expressionRecord = new ExpressionRecord();
    this.writer = writer;
  }

  public ExpressionClient withExpression(final String expression) {
    expressionRecord.setExpression(expression);
    return this;
  }

  public ExpressionClient withTenantId(final String tenantId) {
    expressionRecord.setTenantId(tenantId);
    return this;
  }

  public ExpressionClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<ExpressionRecordValue> resolve() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY, requestStreamId, requestId, ExpressionIntent.EVALUATE, expressionRecord);
    return expectation.apply(position);
  }

  public Record<ExpressionRecordValue> resolve(final String username) {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ExpressionIntent.EVALUATE,
            username,
            expressionRecord);
    return expectation.apply(position);
  }
}

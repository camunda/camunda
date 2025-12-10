/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue;
import java.util.stream.Stream;

public final class ExpressionRecordStream
    extends ExporterRecordStream<ExpressionRecordValue, ExpressionRecordStream> {

  public ExpressionRecordStream(final Stream<Record<ExpressionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ExpressionRecordStream supply(
      final Stream<Record<ExpressionRecordValue>> wrappedStream) {
    return new ExpressionRecordStream(wrappedStream);
  }

  public ExpressionRecordStream withExpression(final String expression) {
    return valueFilter(v -> v.getExpression().equals(expression));
  }
}

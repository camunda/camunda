/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

import io.camunda.zeebe.protocol.impl.record.value.runtimevariables.RuntimeVariablesRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RuntimeVariablesIntent;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import io.camunda.zeebe.protocol.record.value.RuntimeVariablesRecordValue;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Random;
import org.awaitility.Awaitility;

public final class RuntimeVariablesClient {

  private static final long DEFAULT_KEY = -1L;

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();
  private final RuntimeVariablesRecord record = new RuntimeVariablesRecord();
  private final CommandWriter writer;
  private final CommandResponseWriter responseWriter;

  public RuntimeVariablesClient(
      final CommandWriter writer, final CommandResponseWriter responseWriter) {
    this.writer = writer;
    this.responseWriter = responseWriter;
  }

  public RuntimeVariablesClient withScopeKey(final long scopeKey) {
    record.setScopeKey(scopeKey);
    return this;
  }

  public RuntimeVariablesClient withScope(final RuntimeVariableScope scope) {
    record.setScope(scope);
    return this;
  }

  public RuntimeVariablesRecord fetch() {
    writer.writeCommand(
        DEFAULT_KEY, requestStreamId, requestId, RuntimeVariablesIntent.FETCH, record);
    Awaitility.await("runtime variables response")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(findResponse()).isNotNull());
    return findResponse();
  }

  public RuntimeVariablesRecord fetch(final String username) {
    writer.writeCommand(
        DEFAULT_KEY, requestStreamId, requestId, RuntimeVariablesIntent.FETCH, username, record);
    Awaitility.await("runtime variables response")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(findResponse()).isNotNull());
    return findResponse();
  }

  public Record<RuntimeVariablesRecordValue> fetchRejection() {
    final var position =
        writer.writeCommand(
            DEFAULT_KEY, requestStreamId, requestId, RuntimeVariablesIntent.FETCH, record);
    return findRejection(position);
  }

  public Record<RuntimeVariablesRecordValue> fetchRejection(final String username) {
    final var position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            RuntimeVariablesIntent.FETCH,
            username,
            record);
    return findRejection(position);
  }

  @SuppressWarnings("unchecked")
  private static Record<RuntimeVariablesRecordValue> findRejection(final long sourcePosition) {
    return (Record<RuntimeVariablesRecordValue>)
        (Record<?>)
            RecordingExporter.records()
                .withValueType(ValueType.RUNTIME_VARIABLES)
                .onlyCommandRejections()
                .withSourceRecordPosition(sourcePosition)
                .getFirst();
  }

  private RuntimeVariablesRecord findResponse() {
    return mockingDetails(responseWriter).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("valueWriter"))
        .map(invocation -> invocation.getArgument(0))
        .filter(RuntimeVariablesRecord.class::isInstance)
        .map(RuntimeVariablesRecord.class::cast)
        .reduce((first, second) -> second)
        .orElse(null);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo.AuthDataFormat;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.stream.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BufferedProcessingResultBuilderTest {

  private static final RecordBatchSizePredicate LARGE_BATCH_PREDICATE =
      (count, size) -> size < 100_000;

  @Test
  void shouldWriteRecordWithEnhancedMetadata() {
    // given
    final var builder =
        new BufferedProcessingResultBuilder(
            LARGE_BATCH_PREDICATE, metadata -> metadata.batchOperationReference(12345L));
    final var record = new ProcessInstanceCreationRecord();
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceCreationIntent.CREATE);

    // when
    builder.appendRecordReturnEither(1L, record, metadata);

    // then
    final var result = builder.build();
    final var entries = result.getRecordBatch().entries();
    assertThat(entries).hasSize(1);
    final var entry = (RecordBatchEntry) entries.get(0);
    assertThat(entry.recordMetadata().getBatchOperationReference()).isEqualTo(12345L);
  }

  @Test
  void shouldEnhanceMetadataOnWithResponse() {
    // given
    final var builder =
        new BufferedProcessingResultBuilder(
            LARGE_BATCH_PREDICATE, metadata -> metadata.batchOperationReference(12345L));
    final var record = new ProcessInstanceCreationRecord();

    // when
    builder.withResponse(
        RecordType.EVENT,
        1L,
        ProcessInstanceCreationIntent.CREATED,
        record,
        ValueType.PROCESS_INSTANCE_CREATION,
        RejectionType.NULL_VAL,
        "",
        123L,
        456);

    // then
    final var result = builder.build();
    final var response = result.getProcessingResponse().orElseThrow();
    final var responseEntry = response.responseValue();
    assertThat(responseEntry.recordMetadata().getBatchOperationReference()).isEqualTo(12345L);
  }

  @Test
  void shouldResetAuthorizationWhenSetByDecorator() {
    // given
    final Consumer<RecordMetadata> authorizationDecorator =
        metadata -> {
          final var authInfo = new AuthInfo();
          authInfo.setFormat(AuthDataFormat.JWT);
          authInfo.setAuthData("secret-token");
          metadata.authorization(authInfo);
        };
    final var builder =
        new BufferedProcessingResultBuilder(LARGE_BATCH_PREDICATE, authorizationDecorator);
    final var record = new ProcessInstanceCreationRecord();

    // when
    builder.withResponse(
        RecordType.EVENT,
        1L,
        ProcessInstanceCreationIntent.CREATED,
        record,
        ValueType.PROCESS_INSTANCE_CREATION,
        RejectionType.NULL_VAL,
        "",
        123L,
        456);

    // then
    final var result = builder.build();
    final var response = result.getProcessingResponse().orElseThrow();
    final var responseEntry = response.responseValue();
    final var authorization = responseEntry.recordMetadata().getAuthorization();
    assertThat(authorization.getFormat()).isEqualTo(AuthDataFormat.UNKNOWN);
    assertThat(authorization.getAuthData()).isEmpty();
  }
}

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
import io.camunda.zeebe.protocol.impl.encoding.InternalOperationReference;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.InternalOperationType;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.stream.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.List;
import java.util.Map;
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
  void shouldNotEnhanceMetadataOfIsolatedCommand() {
    // given
    final var builder =
        new BufferedProcessingResultBuilder(
            LARGE_BATCH_PREDICATE, metadata -> metadata.batchOperationReference(12345L));

    // when
    builder.appendRecordReturnEither(
        1L,
        new ProcessInstanceCreationRecord(),
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceCreationIntent.CREATE));
    builder.appendIsolatedCommand(
        2L,
        new ProcessInstanceCreationRecord(),
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceCreationIntent.CREATE));

    // then
    final var result = builder.build();
    final var entries = result.getRecordBatch().entries();
    assertThat(entries)
        .extracting(
            entry -> ((RecordBatchEntry) entry).recordMetadata().getBatchOperationReference())
        .containsExactly(12345L, RecordMetadataEncoder.batchOperationReferenceNullValue());
    assertThat(result.isIsolatedCommand(0)).isFalse();
    assertThat(result.isIsolatedCommand(1)).isTrue();
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
  void shouldAppendInternalOperationReferenceToFollowUps() {
    // given
    final var reference = InternalOperationReference.policyUpdate(123L);
    final var builder = new BufferedProcessingResultBuilder(LARGE_BATCH_PREDICATE);
    builder.appendInternalOperationReferenceToFollowUps(() -> reference);

    // when
    for (final var recordType :
        List.of(RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND_REJECTION)) {
      builder.appendRecordReturnEither(
          1L,
          new ProcessInstanceCreationRecord(),
          new RecordMetadata().recordType(recordType).intent(ProcessInstanceCreationIntent.CREATE));
    }

    // then
    assertThat(builder.build().getRecordBatch().entries())
        .extracting(entry -> ((RecordBatchEntry) entry).recordMetadata().getInternalOperation())
        .containsOnly(reference);
  }

  @Test
  void shouldPassOnUnknownInternalOperationTypeCode() {
    // given - a command written by a newer version with a type code this version doesn't know
    final var reference = InternalOperationReference.ofStored(200, 123L);
    final var builder = new BufferedProcessingResultBuilder(LARGE_BATCH_PREDICATE);
    builder.appendInternalOperationReferenceToFollowUps(() -> reference);

    // when
    builder.appendRecordReturnEither(
        1L,
        new ProcessInstanceCreationRecord(),
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(ProcessInstanceCreationIntent.CREATED));

    // then
    final var metadata =
        ((RecordBatchEntry) builder.build().getRecordBatch().entries().getFirst()).recordMetadata();
    assertThat(metadata.getInternalOperation().type()).isEqualTo(InternalOperationType.UNKNOWN);
    assertThat(metadata.getInternalOperationTypeCode()).isEqualTo(200);
  }

  @Test
  void shouldKeepInternalOperationReferenceOfFollowUp() {
    // given
    final var builder = new BufferedProcessingResultBuilder(LARGE_BATCH_PREDICATE);
    builder.appendInternalOperationReferenceToFollowUps(
        () -> InternalOperationReference.policyUpdate(123L));
    final var ownReference = InternalOperationReference.policyUpdate(456L);

    // when
    builder.appendRecordReturnEither(
        1L,
        new ProcessInstanceCreationRecord(),
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceCreationIntent.CREATE)
            .internalOperation(ownReference));

    // then
    final var entry = (RecordBatchEntry) builder.build().getRecordBatch().entries().getFirst();
    assertThat(entry.recordMetadata().getInternalOperation()).isEqualTo(ownReference);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/69323")
  void shouldAppendFullClaimsOnlyToCheckedFollowUps() {
    // given
    final var authInfo =
        AuthInfo.ofClaims(
                Map.of(
                    "authorized_anonymous_user",
                    false,
                    "authorized_username",
                    "user",
                    "authorized_client_id",
                    "client",
                    "authorized_tenants",
                    List.of("tenant"),
                    "user_groups_claims",
                    List.of("group"),
                    "user_token_claims",
                    Map.of("sub", "user", "email", "user@example.com")))
            .withFormat(AuthDataFormat.PRE_AUTHORIZED);
    final var builder = new BufferedProcessingResultBuilder(LARGE_BATCH_PREDICATE);
    builder.appendAuthInfoToFollowUps(authInfo);

    // when
    for (final var recordType :
        List.of(RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND_REJECTION)) {
      builder.appendRecordReturnEither(
          1L,
          new ProcessInstanceCreationRecord(),
          new RecordMetadata().recordType(recordType).intent(ProcessInstanceCreationIntent.CREATE));
    }
    builder.appendRecordReturnEither(
        1L,
        new ProcessInstanceCreationRecord(),
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceCreationIntent.CREATE)
            .batchOperationReference(2L));

    // then
    final var authorizations =
        builder.build().getRecordBatch().entries().stream()
            .map(entry -> ((RecordBatchEntry) entry).recordMetadata().getAuthorization())
            .toList();
    final var actorClaims =
        Map.of(
            "authorized_anonymous_user",
            false,
            "authorized_username",
            "user",
            "authorized_client_id",
            "client");
    assertThat(authorizations.get(0).toClaimsMap())
        .as("internal follow-up command")
        .isEqualTo(actorClaims);
    assertThat(authorizations.get(1).getFormat())
        .as("follow-up event")
        .isEqualTo(AuthDataFormat.PRE_AUTHORIZED);
    assertThat(authorizations.get(1).toClaimsMap()).as("follow-up event").isEqualTo(actorClaims);
    assertThat(authorizations.get(2).toClaimsMap()).as("rejection").isEqualTo(actorClaims);
    assertThat(authorizations.get(3)).as("batch operation command").isEqualTo(authInfo);
  }

  @Test
  void shouldResetAuthorizationWhenSetByDecorator() {
    // given
    final Consumer<RecordMetadata> authorizationDecorator =
        metadata -> {
          metadata.authorization(
              AuthInfo.ofClaims(Map.of("authorized_username", "user"))
                  .withFormat(AuthDataFormat.PRE_AUTHORIZED));
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
    assertThat(authorization.hasAnyClaims()).isFalse();
  }
}

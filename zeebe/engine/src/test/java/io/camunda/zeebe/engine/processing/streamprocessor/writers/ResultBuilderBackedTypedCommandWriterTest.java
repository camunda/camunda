/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.protocol.impl.encoding.InternalOperationReference;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Processors mark the commands they write as part of an internal operation through {@link
 * FollowUpCommandMetadata}, so the writer must carry the reference over to the written record.
 */
final class ResultBuilderBackedTypedCommandWriterTest {

  @Test
  void shouldWriteInternalOperationReferenceOfFollowUpCommand() {
    // given
    final var resultBuilder = mock(ProcessingResultBuilder.class);
    final var writer = new ResultBuilderBackedTypedCommandWriter(() -> resultBuilder);
    final var reference = InternalOperationReference.policyUpdate(123L);

    // when
    writer.appendFollowUpCommand(
        1L,
        RoleIntent.CREATE,
        new RoleRecord(),
        FollowUpCommandMetadata.of(b -> b.internalOperationReference(reference)));

    // then
    final var metadata = ArgumentCaptor.forClass(RecordMetadata.class);
    verify(resultBuilder).appendRecord(eq(1L), any(), metadata.capture());
    assertThat(metadata.getValue().getInternalOperation()).isEqualTo(reference);
  }

  @Test
  void shouldWriteNoInternalOperationReferenceByDefault() {
    // given
    final var resultBuilder = mock(ProcessingResultBuilder.class);
    final var writer = new ResultBuilderBackedTypedCommandWriter(() -> resultBuilder);

    // when
    writer.appendFollowUpCommand(1L, RoleIntent.CREATE, new RoleRecord());

    // then
    final var metadata = ArgumentCaptor.forClass(RecordMetadata.class);
    verify(resultBuilder).appendRecord(eq(1L), any(), metadata.capture());
    assertThat(metadata.getValue().getInternalOperation().isPresent()).isFalse();
  }
}

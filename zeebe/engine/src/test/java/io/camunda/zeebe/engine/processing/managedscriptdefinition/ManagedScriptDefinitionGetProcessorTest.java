/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.managedscriptdefinition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableManagedScriptDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

final class ManagedScriptDefinitionGetProcessorTest {

  @Test
  void shouldGetDefinitionByProcessDefinitionAndElement() {
    // given
    final long definitionKey = 456L;
    final var definition =
        new ManagedScriptDefinitionRecord().setManagedScriptDefinitionKey(definitionKey);
    final var managedScriptDefinitionState = mock(MutableManagedScriptDefinitionState.class);
    when(managedScriptDefinitionState.getManagedScriptDefinitionKey(
            eq(123L), any(DirectBuffer.class)))
        .thenReturn(definitionKey);
    when(managedScriptDefinitionState.getManagedScriptDefinition(definitionKey))
        .thenReturn(definition);
    final var processingState = mock(MutableProcessingState.class);
    when(processingState.getManagedScriptDefinitionState())
        .thenReturn(managedScriptDefinitionState);
    final var responseWriter = mock(TypedResponseWriter.class);
    final var writers = mock(Writers.class);
    when(writers.response()).thenReturn(responseWriter);
    when(writers.rejection()).thenReturn(mock(TypedRejectionWriter.class));
    final var processor = new ManagedScriptDefinitionGetProcessor(processingState, writers);
    final var request =
        new ManagedScriptDefinitionRecord().setProcessDefinitionKey(123L).setElementId("script");
    final var command = new MockTypedRecord<>(-1L, new RecordMetadata(), request);

    // when
    processor.processRecord(command);

    // then
    verify(managedScriptDefinitionState)
        .getManagedScriptDefinitionKey(eq(123L), any(DirectBuffer.class));
    verify(responseWriter)
        .writeAcceptedResponseOnCommand(
            definitionKey, ManagedScriptDefinitionIntent.GOT, definition, command);
  }
}

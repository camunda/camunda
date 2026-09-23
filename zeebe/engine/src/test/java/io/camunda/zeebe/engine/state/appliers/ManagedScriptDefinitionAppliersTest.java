/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.engine.state.mutable.MutableManagedScriptDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ManagedScriptDefinitionAppliersTest {

  private MutableProcessingState processingState;
  private MutableManagedScriptDefinitionState state;
  private ManagedScriptDefinitionCreatedApplier createdApplier;
  private ManagedScriptDefinitionDeletedApplier deletedApplier;

  @BeforeEach
  void setUp() {
    state = processingState.getManagedScriptDefinitionState();
    createdApplier = new ManagedScriptDefinitionCreatedApplier(state);
    deletedApplier = new ManagedScriptDefinitionDeletedApplier(state);
  }

  @Test
  void shouldApplyCreatedEventIdempotently() {
    // given
    final var record = sampleRecord();

    // when
    createdApplier.applyState(record.getManagedScriptDefinitionKey(), record);

    // then
    assertThatCode(() -> createdApplier.applyState(record.getManagedScriptDefinitionKey(), record))
        .doesNotThrowAnyException();
    assertThat(state.getManagedScriptDefinition(record.getManagedScriptDefinitionKey()))
        .isNotNull();
    assertThat(
            state.getManagedScriptDefinitionKey(
                record.getProcessDefinitionKey(), BufferUtil.wrapString(record.getElementId())))
        .isEqualTo(record.getManagedScriptDefinitionKey());
  }

  @Test
  void shouldApplyDeletedEventIdempotently() {
    // given
    final var record = sampleRecord();
    createdApplier.applyState(record.getManagedScriptDefinitionKey(), record);

    // when
    deletedApplier.applyState(record.getManagedScriptDefinitionKey(), record);

    // then
    assertThatCode(() -> deletedApplier.applyState(record.getManagedScriptDefinitionKey(), record))
        .doesNotThrowAnyException();
    assertThat(state.getManagedScriptDefinition(record.getManagedScriptDefinitionKey())).isNull();
    assertThat(
            state.getManagedScriptDefinitionKey(
                record.getProcessDefinitionKey(), BufferUtil.wrapString(record.getElementId())))
        .isNull();
  }

  private static ManagedScriptDefinitionRecord sampleRecord() {
    return new ManagedScriptDefinitionRecord()
        .setManagedScriptDefinitionKey(42L)
        .setStatus(ManagedScriptDefinitionStatus.PENDING)
        .setResourceKey(7L)
        .setResourceName("script.js")
        .setArtifactDigest(BufferUtil.wrapString("digest"))
        .setElementId("script-task")
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(3L)
        .setProcessDefinitionVersion(1)
        .setLanguage("javascript")
        .setRuntime("nodejs22")
        .setTenantId("<default>");
  }
}

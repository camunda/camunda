/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.managedscriptdefinition;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableManagedScriptDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ManagedScriptDefinitionStateTest {

  private MutableProcessingState processingState;
  private MutableManagedScriptDefinitionState state;

  @BeforeEach
  void setUp() {
    state = processingState.getManagedScriptDefinitionState();
  }

  @Test
  void shouldStoreAndLookUpDefinition() {
    // given
    final var record = sampleRecord(42L, 3L, "script-task");

    // when
    state.insert(record.getManagedScriptDefinitionKey(), record);

    // then
    assertThat(
            state.getManagedScriptDefinitionKey(
                record.getProcessDefinitionKey(), BufferUtil.wrapString(record.getElementId())))
        .isEqualTo(record.getManagedScriptDefinitionKey());
    final var storedDefinition =
        state.getManagedScriptDefinition(record.getManagedScriptDefinitionKey());
    assertThat(storedDefinition.getArtifactDigest()).containsExactly("digest".getBytes(UTF_8));
    assertThat(storedDefinition)
        .extracting(
            ManagedScriptDefinitionRecord::getStatus,
            ManagedScriptDefinitionRecord::getResourceKey,
            ManagedScriptDefinitionRecord::getResourceName,
            ManagedScriptDefinitionRecord::getElementId,
            ManagedScriptDefinitionRecord::getBpmnProcessId,
            ManagedScriptDefinitionRecord::getProcessDefinitionKey,
            ManagedScriptDefinitionRecord::getProcessDefinitionVersion,
            ManagedScriptDefinitionRecord::getProcessDefinitionVersionTag,
            ManagedScriptDefinitionRecord::getLanguage,
            ManagedScriptDefinitionRecord::getRuntime,
            ManagedScriptDefinitionRecord::getTenantId)
        .containsExactly(
            ManagedScriptDefinitionStatus.PENDING,
            7L,
            "script.js",
            "script-task",
            "process",
            3L,
            2,
            "v2",
            "javascript",
            "nodejs22",
            "tenant");
  }

  @Test
  void shouldEnumerateOnlyDefinitionsForProcessDefinition() {
    // given
    state.insert(1L, sampleRecord(1L, 10L, "script-a"));
    state.insert(2L, sampleRecord(2L, 10L, "script-b"));
    state.insert(3L, sampleRecord(3L, 20L, "script-c"));

    // when
    final List<Long> keys = new ArrayList<>();
    state.forEachManagedScriptDefinitionKey(10L, keys::add);

    // then
    assertThat(keys).containsExactlyInAnyOrder(1L, 2L);
  }

  private static ManagedScriptDefinitionRecord sampleRecord(
      final long definitionKey, final long processDefinitionKey, final String elementId) {
    return new ManagedScriptDefinitionRecord()
        .setManagedScriptDefinitionKey(definitionKey)
        .setStatus(ManagedScriptDefinitionStatus.PENDING)
        .setResourceKey(7L)
        .setResourceName("script.js")
        .setArtifactDigest(BufferUtil.wrapString("digest"))
        .setElementId(elementId)
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(2)
        .setProcessDefinitionVersionTag("v2")
        .setLanguage("javascript")
        .setRuntime("nodejs22")
        .setTenantId("tenant");
  }
}

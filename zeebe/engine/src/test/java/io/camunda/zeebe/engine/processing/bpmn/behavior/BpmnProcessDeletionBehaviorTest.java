/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link
 * BpmnProcessDeletionBehavior#finalizeDrainingDefinitionsWithoutLocalInstances()}.
 */
@ExtendWith(ProcessingStateExtension.class)
final class BpmnProcessDeletionBehaviorTest {

  private static final String OTHER_TENANT = "other-tenant";

  private MutableProcessingState processingState;

  private final StateWriter stateWriter = mock(StateWriter.class);
  private final TypedCommandWriter commandWriter = mock(TypedCommandWriter.class);
  private BpmnProcessDeletionBehavior behavior;
  private long nextKey = 1L;

  @BeforeEach
  void setUp() {
    behavior =
        new BpmnProcessDeletionBehavior(
            processingState.getProcessState(),
            processingState.getElementInstanceState(),
            processingState.getBannedInstanceState(),
            commandWriter,
            stateWriter,
            processingState.getKeyGenerator());
  }

  @Test
  void shouldReportDrainForEveryDrainingDefinitionWithoutInstances() {
    // given - several draining, instance-free definitions: two versions of one process id and one
    // under a non-default tenant, covering the all-version/all-tenant scan
    final long keyV1 = putProcess("process", 1, TenantOwned.DEFAULT_TENANT_IDENTIFIER, true);
    final long keyV2 = putProcess("process", 2, TenantOwned.DEFAULT_TENANT_IDENTIFIER, true);
    final long keyOtherTenant = putProcess("other-process", 1, OTHER_TENANT, true);

    // when
    behavior.finalizeDrainingDefinitionsWithoutLocalInstances();

    // then - each definition is removed locally and its drain reported to the deployment partition
    assertThat(reportedDrainKeys()).containsExactlyInAnyOrder(keyV1, keyV2, keyOtherTenant);
    assertThat(appendedEventKeys(ProcessIntent.DELETING))
        .containsExactlyInAnyOrder(keyV1, keyV2, keyOtherTenant);
    assertThat(appendedEventKeys(ProcessIntent.DELETED))
        .containsExactlyInAnyOrder(keyV1, keyV2, keyOtherTenant);
  }

  @Test
  void shouldNotReportDrainForActiveDefinition() {
    // given - one draining and one ACTIVE (non-draining) definition
    final long drainingKey = putProcess("draining", 1, TenantOwned.DEFAULT_TENANT_IDENTIFIER, true);
    putProcess("active", 1, TenantOwned.DEFAULT_TENANT_IDENTIFIER, false);

    // when
    behavior.finalizeDrainingDefinitionsWithoutLocalInstances();

    // then - only the draining definition is finalized; the ACTIVE one is left untouched
    assertThat(reportedDrainKeys()).containsExactly(drainingKey);
  }

  @Test
  void shouldNotReportDrainWhileDrainingDefinitionStillHasActiveInstance() {
    // given - a draining definition that still has a local active instance
    final long drainingKey = putProcess("draining", 1, TenantOwned.DEFAULT_TENANT_IDENTIFIER, true);
    putActiveProcessInstance(drainingKey, "draining");

    // when
    behavior.finalizeDrainingDefinitionsWithoutLocalInstances();

    // then - the guard keeps it draining: nothing is reported or removed
    verifyNoInteractions(commandWriter);
    verifyNoInteractions(stateWriter);
  }

  private long putProcess(
      final String processId, final int version, final String tenantId, final boolean draining) {
    final long key = nextKey++;
    final var processRecord =
        new ProcessRecord()
            .setKey(key)
            .setBpmnProcessId(processId)
            .setVersion(version)
            .setResourceName(processId + ".bpmn")
            .setResource(
                wrapString(
                    Bpmn.convertToString(
                        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())))
            .setTenantId(tenantId);
    processingState.getProcessState().putProcess(key, processRecord);
    if (draining) {
      processingState
          .getProcessState()
          .updateProcessState(processRecord, PersistedProcessState.DRAINING);
    }
    return key;
  }

  private void putActiveProcessInstance(final long processDefinitionKey, final String processId) {
    final long instanceKey = nextKey++;
    final var record =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(instanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(processId)
            .setElementId(processId)
            .setBpmnElementType(BpmnElementType.PROCESS);
    processingState
        .getElementInstanceState()
        .newInstance(instanceKey, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private List<Long> reportedDrainKeys() {
    final var captor = ArgumentCaptor.forClass(ProcessRecord.class);
    verify(commandWriter, atLeast(0))
        .appendFollowUpCommand(anyLong(), eq(ProcessIntent.DELETE_COMPLETE), captor.capture());
    return captor.getAllValues().stream().map(ProcessRecord::getProcessDefinitionKey).toList();
  }

  private List<Long> appendedEventKeys(final ProcessIntent intent) {
    final var captor = ArgumentCaptor.forClass(ProcessRecord.class);
    verify(stateWriter, atLeast(0)).appendFollowUpEvent(anyLong(), eq(intent), captor.capture());
    return captor.getAllValues().stream().map(ProcessRecord::getProcessDefinitionKey).toList();
  }
}

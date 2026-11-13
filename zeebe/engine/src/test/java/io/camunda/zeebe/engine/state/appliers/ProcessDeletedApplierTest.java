/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.engine.state.deployment.ProcessStateTest.creatingProcessRecord;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ProcessDeletedApplierTest {

  private static final String VERSION_TAG = "shared";

  private MutableProcessingState processingState;

  private MutableProcessState processState;
  private EventAppliers eventAppliers;
  private String processId;
  private ProcessRecord v1;
  private ProcessRecord v2;

  @BeforeEach
  void setUp() {
    processState = processingState.getProcessState();
    eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);

    // given two versions sharing a version tag, the lookup points at the latest one
    processId = Strings.newRandomValidBpmnId();
    v1 = putProcessWithVersionTag(1);
    v2 = putProcessWithVersionTag(2);
  }

  @Test
  void shouldRemoveVersionTagLookupOnReplayOfVersion1() {
    // when
    eventAppliers.applyState(v1.getProcessDefinitionKey(), ProcessIntent.DELETED, v1, 1);

    // then
    assertThat(getProcessByVersionTag()).isNull();
  }

  @Test
  void shouldKeepVersionTagLookupOfOtherVersionOnVersion2() {
    // when
    eventAppliers.applyState(v1.getProcessDefinitionKey(), ProcessIntent.DELETED, v1, 2);

    // then
    assertThat(getProcessByVersionTag())
        .extracting(DeployedProcess::getKey)
        .isEqualTo(v2.getProcessDefinitionKey());
  }

  @Test
  void shouldRepointVersionTagLookupToPreviousVersionOnVersion2() {
    // when
    eventAppliers.applyState(v2.getProcessDefinitionKey(), ProcessIntent.DELETED, v2, 2);

    // then
    assertThat(getProcessByVersionTag())
        .extracting(DeployedProcess::getKey)
        .isEqualTo(v1.getProcessDefinitionKey());
  }

  private ProcessRecord putProcessWithVersionTag(final int version) {
    final var processRecord =
        creatingProcessRecord(processingState, processId, version, VERSION_TAG);
    processState.putProcess(processRecord.getKey(), processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    return processRecord;
  }

  private DeployedProcess getProcessByVersionTag() {
    return processState.getProcessByProcessIdAndVersionTag(
        wrapString(processId), VERSION_TAG, v1.getTenantId());
  }
}

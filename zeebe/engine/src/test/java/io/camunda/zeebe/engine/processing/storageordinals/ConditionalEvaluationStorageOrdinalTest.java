/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConditionalStartedProcessInstanceValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * One conditional evaluation can start many instances, so the ordinal lives on each started process
 * instance entry and not on the evaluation record itself.
 */
public final class ConditionalEvaluationStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 7;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignInstanceOrdinalToStartedProcessInstanceEntries() {
    // given
    engine
        .deployment()
        .withXmlResource(
            "first.bpmn",
            Bpmn.createExecutableProcess("conditional-evaluation-ordinal-first")
                .startEvent()
                .condition(c -> c.condition("=x > 10"))
                .endEvent()
                .done())
        .withXmlResource(
            "second.bpmn",
            Bpmn.createExecutableProcess("conditional-evaluation-ordinal-second")
                .startEvent()
                .condition(c -> c.condition("=x > 10"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var evaluated = engine.conditionalEvaluation().withVariable("x", 11).evaluate();

    // then
    final var startedInstances = evaluated.getValue().getStartedProcessInstances();
    assertThat(startedInstances).hasSize(2);
    assertThat(startedInstances)
        .extracting(ConditionalStartedProcessInstanceValue::getStorageOrdinal)
        .containsOnly(FIXED_ORDINAL);

    for (final var startedInstance : startedInstances) {
      final var activating =
          RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
              .withProcessInstanceKey(startedInstance.getProcessInstanceKey())
              .withElementType(BpmnElementType.PROCESS)
              .getFirst();
      assertThat(activating.getValue().getStorageOrdinal())
          .isEqualTo(startedInstance.getStorageOrdinal());
    }
  }
}

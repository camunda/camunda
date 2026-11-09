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
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

public final class EscalationStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 7;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignInstanceOrdinalToEscalatedEvent() {
    // given
    final var processId = "escalation-ordinal-caught-process";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .intermediateThrowEvent(
                                "throw", i -> i.escalation("escalation-ordinal-caught"))
                            .endEvent())
                .boundaryEvent("catch", b -> b.escalation("escalation-ordinal-caught"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.escalationRecords(EscalationIntent.ESCALATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignInstanceOrdinalToNotEscalatedEvent() {
    // given
    final var processId = "escalation-ordinal-uncaught-process";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateThrowEvent("throw", i -> i.escalation("escalation-ordinal-uncaught"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.escalationRecords(EscalationIntent.NOT_ESCALATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }
}

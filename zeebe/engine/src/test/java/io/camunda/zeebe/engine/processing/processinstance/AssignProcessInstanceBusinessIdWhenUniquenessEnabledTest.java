/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that late Business ID assignment is unavailable while Business ID uniqueness is enabled
 * (ADR 0006, D5). Kept separate from {@link AssignProcessInstanceBusinessIdTest} so the engine is
 * configured with uniqueness enabled from the start, avoiding a mid-test configuration change.
 */
public final class AssignProcessInstanceBusinessIdWhenUniquenessEnabledTest {

  private static final String PROCESS_ID = "process";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectAssignmentWhenUniquenessIsEnabled() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId("biz-1")
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }
}

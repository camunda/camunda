/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that late Business ID assignment via {@code CompleteJob} is unavailable while Business
 * ID uniqueness is enabled (ADR 0006, D5/D12). Kept separate from {@link
 * CompleteJobBusinessIdAssignmentTest} so the engine is configured with uniqueness enabled from the
 * start, avoiding a mid-test configuration change.
 */
public final class CompleteJobBusinessIdAssignmentWhenUniquenessEnabledTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "t-assign";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("assign", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCompletionWithAssignmentWhenUniquenessIsEnabled() {
    // given
    engine.deployment().withXmlResource(PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var rejection =
        engine
            .job()
            .ofInstance(processInstanceKey)
            .withType(JOB_TYPE)
            .withBusinessId("biz-1")
            .expectRejection()
            .complete();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to assign a business id to process instance with key '%d', but business id assignment is not allowed while business id uniqueness is enabled"
                .formatted(processInstanceKey));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Regression pin: with {@code businessIdUniquenessEnabled = false} (the default), publishing two
 * messages with the same {@code businessId} that target a message start event must still produce
 * two process instances — the businessId is recorded but never used as a gate. This guards against
 * silently flipping the default or coupling the filter to flag-independent code paths.
 */
public final class MessageStartEventBusinessIdUniquenessDisabledTest {

  private static final String PROCESS_ID = "wf";
  private static final String MESSAGE_NAME = "start-msg";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  // Explicitly disabled to make the regression intent visible at the call site, even though it
  // matches the EngineConfiguration default.
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));

  @Test
  public void shouldAllowDuplicateBusinessIdWhenFlagDisabled() {
    // given
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-42")
        .withVariables(Map.of("seq", 1))
        .publish();
    RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-42")
        .withVariables(Map.of("seq", 2))
        .publish();

    // then two PIs are created, both stamped with the businessId
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(r -> r.getValue().getBusinessId())
        .containsExactly("biz-42", "biz-42");
  }
}

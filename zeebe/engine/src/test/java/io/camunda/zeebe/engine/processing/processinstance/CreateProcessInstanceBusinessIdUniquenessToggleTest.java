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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for toggle transitions of the {@code businessIdUniquenessEnabled} configuration. The
 * business ID index is always maintained regardless of the toggle — only validation is gated. These
 * tests verify that validation behaves correctly after toggle transitions.
 */
public final class CreateProcessInstanceBusinessIdUniquenessToggleTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              config -> {
                // Several tests depend on this specific default as the starting point
                config.setBusinessIdUniquenessEnabled(false);
              });

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectDuplicateAfterEnablingUniqueness() {
    // given — uniqueness OFF (default)
    final String processId = "process";
    final String businessId = "biz-1";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withBusinessId(businessId)
        .withTags("duplicate")
        .create();

    // when — toggle ON
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    engine.start();

    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withBusinessId(businessId)
        .withTags("afterToggle")
        .expectRejection()
        .create();

    // then — new create with same businessId is rejected
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withTags("afterToggle")
                .withBpmnProcessId(processId)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            """
            Expected to create instance of process with business id '%s', \
            but an instance with this business id already exists for process definition '%s'\
            """
                .formatted(businessId, processId));
  }

  @Test
  public void shouldAllowDuplicateAfterDisablingUniqueness() {
    // given — toggle ON
    final String processId = "process";
    final String businessId = "biz-1";
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    engine.start();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when — toggle OFF
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    engine.start();

    // then — duplicate is allowed
    final var secondInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTags("afterToggle")
            .create();
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .withTags("afterToggle")
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(secondInstanceKey);
  }

  @Test
  public void shouldAllowReuseAfterDuplicateCompletesAndUniquenessEnabled() {
    // given — uniqueness OFF (default)
    final String processId = "process";
    final String businessId = "biz-1";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var instanceKeyA =
        engine.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    final var instanceKeyB =
        engine.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when — toggle ON, then complete both instances
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    engine.start();
    engine.userTask().ofInstance(instanceKeyA).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(instanceKeyA)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    engine.userTask().ofInstance(instanceKeyB).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(instanceKeyB)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then — business ID is freed, new create succeeds
    final var instanceKeyC =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTags("afterCompletion")
            .create();
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .withTags("afterCompletion")
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(instanceKeyC);
  }

  @Test
  public void shouldFreeBusinessIdWhenInstanceCompletesDuringDisabledWindow() {
    // given — toggle ON
    final String processId = "process";
    final String businessId = "biz-1";
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    engine.start();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var instanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when — toggle OFF, complete instance, toggle ON
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    engine.start();
    engine.userTask().ofInstance(instanceKey).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(instanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    engine.stop();
    engine.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    engine.start();

    // then — business ID is freed
    final var newInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withTags("afterReEnable")
            .create();
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withBpmnProcessId(processId)
                .withTags("afterReEnable")
                .getFirst()
                .getValue())
        .hasBusinessId(businessId)
        .hasProcessInstanceKey(newInstanceKey);
  }
}

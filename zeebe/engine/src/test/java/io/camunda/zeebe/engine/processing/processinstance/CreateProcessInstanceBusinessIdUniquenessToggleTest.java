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
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for toggle transitions of the {@code businessIdUniquenessEnabled} configuration. The
 * business ID index is always maintained regardless of the toggle — only validation is gated. These
 * tests verify that validation behaves correctly after toggle transitions.
 */
public final class CreateProcessInstanceBusinessIdUniquenessToggleTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectDuplicateAfterEnablingUniqueness() {
    // given — uniqueness OFF (default)
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-1";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withBusinessId(businessId)
        .withTags("duplicate")
        .create();

    // when — toggle ON
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    ENGINE.start();

    ENGINE
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

    // cleanup — disable uniqueness for other tests
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    ENGINE.start();
  }

  @Test
  public void shouldAllowDuplicateAfterDisablingUniqueness() {
    // given — toggle ON
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-1";
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    ENGINE.start();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when — toggle OFF
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    ENGINE.start();

    // then — duplicate is allowed
    final var secondInstanceKey =
        ENGINE
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
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-1";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var instanceKeyA =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();
    final var instanceKeyB =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when — toggle ON, then complete both instances
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    ENGINE.start();
    ENGINE.userTask().ofInstance(instanceKeyA).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(instanceKeyA)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    ENGINE.userTask().ofInstance(instanceKeyB).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(instanceKeyB)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then — business ID is freed, new create succeeds
    final var instanceKeyC =
        ENGINE
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

    // cleanup — disable uniqueness for other tests
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    ENGINE.start();
  }

  @Test
  public void shouldFreeBusinessIdWhenInstanceCompletesDuringDisabledWindow() {
    // given — toggle ON
    final String processId = helper.getBpmnProcessId();
    final String businessId = "biz-1";
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    ENGINE.start();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .deploy();
    final var instanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(businessId).create();

    // when — toggle OFF, complete instance, toggle ON
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    ENGINE.start();
    ENGINE.userTask().ofInstance(instanceKey).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(instanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));
    ENGINE.start();

    // then — business ID is freed
    final var newInstanceKey =
        ENGINE
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

    // cleanup — disable uniqueness for other tests
    ENGINE.stop();
    ENGINE.withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));
    ENGINE.start();
  }
}

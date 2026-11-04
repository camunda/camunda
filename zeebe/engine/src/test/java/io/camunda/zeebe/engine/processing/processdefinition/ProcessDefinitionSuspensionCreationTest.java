/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public class ProcessDefinitionSuspensionCreationTest {

  private static final String SUSPENDED_MESSAGE =
      "Expected to create an instance of process with key '%d', but the process definition is suspended";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long deploy(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  @Test
  public void shouldRejectCreateByKeyWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deploy(processId);
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId("")
        .withProcessDefinitionKey(key)
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(SUSPENDED_MESSAGE.formatted(key));
  }

  @Test
  public void shouldRejectCreateByIdAndVersionWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deploy(processId);
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVersion(1).expectRejection().create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(SUSPENDED_MESSAGE.formatted(key));
  }

  @Test
  public void shouldRejectCreateWithResultWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deploy(processId);
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    final var rejection =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVersion(1)
            .withResult()
            .createExpectingRejection();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(SUSPENDED_MESSAGE.formatted(key));
  }

  @Test
  public void shouldRejectCreateWithStartInstructionsWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deploy(processId);
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersion(1)
        .withStartInstruction("task")
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(SUSPENDED_MESSAGE.formatted(key));
  }

  @Test
  public void shouldCreateOnLatestActiveVersionWhenLatestSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long v1 = deploy(processId);
    final long v2 = deployVersion2(processId);
    engine.processDefinitionSuspension().withKey(v2).suspend();

    // when
    final long instanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var created =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(instanceKey)
            .getFirst();
    assertThat(created.getValue().getProcessDefinitionKey()).isEqualTo(v1);
    assertThat(created.getValue().getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldRejectCreateByIdWhenAllVersionsSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deploy(processId);
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to find process definition with process ID '%s', but none found"
                .formatted(processId));
  }

  @Test
  public void shouldCreateAgainAfterResume() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deploy(processId);
    engine.processDefinitionSuspension().withKey(key).suspend();
    engine.processDefinitionSuspension().withKey(key).resume();

    // when
    final long instanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withInstanceKey(instanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCreateNewVersionWhenRedeployingWhileLatestSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long v1 = deploy(processId);
    engine.processDefinitionSuspension().withKey(v1).suspend();

    // when
    final var redeployed =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    // then
    assertThat(redeployed.getVersion()).isEqualTo(2);
    assertThat(redeployed.isDuplicate()).isFalse();
    assertThat(redeployed.getProcessDefinitionKey()).isNotEqualTo(v1);
  }

  private long deployVersion2(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task-v2"))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }
}

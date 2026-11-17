/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.validation.ValidationConstraints;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class VariableNestingDepthRejectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final int MAX_DEPTH = ValidationConstraints.MAX_NESTING_DEPTH;
  private static final String EXPECTED_REJECTION_REASON =
      "Expected document to be nested at most %d levels deep, but it exceeds that limit"
          .formatted(MAX_DEPTH);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String processId;
  private String jobType;

  @Before
  public void setUp() {
    processId = helper.getBpmnProcessId();
    jobType = helper.getJobType();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldRejectProcessInstanceCreationWithDeeplyNestedVariables() {
    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(deeplyNestedDocument())
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectProcessInstanceCreationWithAwaitingResultWithDeeplyNestedVariables() {
    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(deeplyNestedDocument())
        .withResult()
        .createExpectingRejection();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectVariableDocumentUpdateWithDeeplyNestedVariables() {
    // given
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(deeplyNestedDocument())
        .expectRejection()
        .update();

    // then
    final var rejection =
        RecordingExporter.variableDocumentRecords().onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectJobCompleteWithDeeplyNestedVariables() {
    // given
    ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var jobs = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = jobs.getValue().getJobKeys().getFirst();

    // when
    ENGINE.job().withKey(jobKey).withVariables(deeplyNestedDocument()).expectRejection().complete();

    // then
    final var rejection = RecordingExporter.jobRecords().onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectJobFailWithDeeplyNestedVariables() {
    // given
    ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var jobs = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = jobs.getValue().getJobKeys().getFirst();

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withVariables(deeplyNestedDocument())
        .withRetries(3)
        .expectRejection()
        .fail();

    // then
    final var rejection = RecordingExporter.jobRecords().onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectMessagePublishWithDeeplyNestedVariables() {
    // when
    ENGINE
        .message()
        .withName("msg")
        .withCorrelationKey("key")
        .withVariables(deeplyNestedDocument())
        .expectRejection()
        .publish();

    // then
    final var rejection =
        RecordingExporter.messageRecords(MessageIntent.PUBLISH).onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectSignalBroadcastWithDeeplyNestedVariables() {
    // when
    ENGINE
        .signal()
        .withSignalName("signal")
        .withVariables(deeplyNestedDocument())
        .expectRejection()
        .broadcast();

    // then
    final var rejection =
        RecordingExporter.signalRecords(SignalIntent.BROADCAST).onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectProcessInstanceModificationWithDeeplyNestedVariables() {
    // given
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("task")
        .withVariables("task", deeplyNestedDocument())
        .expectRejection()
        .modify();

    // then
    final var rejection =
        RecordingExporter.processInstanceModificationRecords(
                ProcessInstanceModificationIntent.MODIFY)
            .onlyCommandRejections()
            .getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.PROCESSING_ERROR);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectAdHocSubProcessActivationWithDeeplyNestedElementVariables() {
    // given
    final String adHocProcessId = helper.getBpmnProcessId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(adHocProcessId)
                .startEvent()
                .adHocSubProcess("ad-hoc", b -> b.task("A"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(adHocProcessId).create();

    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .map(Record::getKey)
            .limit(1)
            .findFirst()
            .orElseThrow();

    // when
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIdAndVariables("A", deeplyNestedDocument())
        .expectRejection()
        .activate();

    // then
    final var rejection =
        RecordingExporter.adHocSubProcessInstructionRecords().onlyCommandRejections().getFirst();

    assertThat(rejection).hasRejectionType(RejectionType.PROCESSING_ERROR);
    assertThat(rejection)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  @Test
  public void shouldRejectScriptTaskWithDeeplyNestedExpression() {
    // given
    final String deeplyNestedFeelExpression =
        String.format(
            "(for i in 1..%s return if i = 1 then [\"test\"] else [partial[-1]])[-1]",
            MAX_DEPTH + 1);
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .scriptTask(
                    "script-task",
                    b ->
                        b.zeebeExpression(deeplyNestedFeelExpression).zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incident)
        .extracting(r -> ((IncidentRecordValue) r.getValue()).getErrorType())
        .isEqualTo(ErrorType.IO_MAPPING_ERROR);
    assertThat(incident)
        .extracting(r -> ((IncidentRecordValue) r.getValue()).getErrorMessage())
        .asString()
        .contains(EXPECTED_REJECTION_REASON);
  }

  private static Map<String, Object> deeplyNestedDocument() {
    Map<String, Object> current = Collections.emptyMap();
    for (int i = MAX_DEPTH; i >= 0; i--) {
      current = Collections.singletonMap(String.valueOf(i), current);
    }
    return current;
  }
}

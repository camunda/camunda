/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CaseCreationTest {

  private static final int FIXED_STORAGE_ORDINAL = 1234;

  // A fixed, non-default storage ordinal makes the assertion that the case takes over the ordinal
  // of its process instance meaningful.
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              config ->
                  config
                      .setArchiverlessEnabled(true)
                      .setFixedStorageOrdinal(FIXED_STORAGE_ORDINAL));

  private static final String TITLE_VARIABLE = "caseTitle";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateCaseWithTitleFromCreationVariable() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "my case")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "my case");
  }

  @Test
  public void shouldCreateCaseForProcessThatCompletesStraightAway() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "short-lived case")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "short-lived case");
  }

  @Test
  public void shouldCreateCaseWhenCreatingWithStartInstructions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .userTask("task")
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "instructed case")
            .withStartInstruction("task")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "instructed case");
  }

  @Test
  public void shouldCreateCaseWhenCreatingWithAwaitingResult() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "awaited case")
            .withResult()
            .withRequestId(1L)
            .withRequestStreamId(1)
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "awaited case");
  }

  @Test
  public void shouldCreateCaseWhenMessageStartEventTriggers() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .message(messageName)
                .userTask()
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("key-" + processId)
        .withVariables(Map.of(TITLE_VARIABLE, "message case"))
        .publish();

    // then
    assertCaseCreated(awaitProcessInstanceKey(processId), processId, "message case");
  }

  @Test
  public void shouldCreateCaseWhenBufferedMessageStartsNewInstance() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var correlationKey = "key-" + processId;
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .message(messageName)
                .serviceTask("task", task -> task.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();

    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withVariables(Map.of(TITLE_VARIABLE, "first case"))
        .publish();
    final var firstProcessInstanceKey = awaitProcessInstanceKey(processId);

    // the instance holds the correlation key, so the message is buffered
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withVariables(Map.of(TITLE_VARIABLE, "buffered case"))
        .publish();

    // when
    ENGINE.job().ofInstance(firstProcessInstanceKey).withType(jobType).complete();

    // then
    final var secondProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .skip(1)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    assertCaseCreated(firstProcessInstanceKey, processId, "first case");
    assertCaseCreated(secondProcessInstanceKey, processId, "buffered case");
  }

  @Test
  public void shouldCreateCaseWhenTimerStartEventTriggers() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .timerWithCycle("R1/PT1S")
                .userTask()
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    final var processInstanceKey = awaitProcessInstanceKey(processId);
    assertCaseCreated(processInstanceKey, processId, String.valueOf(processInstanceKey));
  }

  @Test
  public void shouldCreateCaseWhenSignalStartEventTriggers() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var signalName = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .signal(signalName)
                .userTask()
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE
        .signal()
        .withSignalName(signalName)
        .withVariables(Map.of(TITLE_VARIABLE, "signal case"))
        .broadcast();

    // then
    assertCaseCreated(awaitProcessInstanceKey(processId), processId, "signal case");
  }

  @Test
  public void shouldCreateCaseWhenConditionalStartEventTriggers() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .condition(c -> c.condition("=caseCondition = \"" + processId + "\""))
                .userTask()
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE
        .conditionalEvaluation()
        .withVariables(Map.of("caseCondition", processId, TITLE_VARIABLE, "conditional case"))
        .evaluate();

    // then
    assertCaseCreated(awaitProcessInstanceKey(processId), processId, "conditional case");
  }

  @Test
  public void shouldUseProcessInstanceKeyAsTitleWhenTitleVariableIsMissing() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("unrelated", "value")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, String.valueOf(processInstanceKey));
  }

  @Test
  public void shouldUseIntegerTitleVariableAsTitle() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, 12345)
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "12345");
  }

  @Test
  public void shouldUseFloatTitleVariableAsTitle() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, 1.5)
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "1.5");
  }

  @Test
  public void shouldUseBooleanTitleVariableAsTitle() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, true)
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "true");
  }

  @Test
  public void shouldFindTitleVariableAfterNestedVariables() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{\"other\": {\"a\": [1, 2]}, \"" + TITLE_VARIABLE + "\": \"x\"}")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "x");
  }

  @Test
  public void shouldUseProcessInstanceKeyAsTitleWhenTitleVariableIsObject() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, Map.of("name", "my case"))
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, String.valueOf(processInstanceKey));
  }

  @Test
  public void shouldUseProcessInstanceKeyAsTitleWhenTitleVariableIsEmpty() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, String.valueOf(processInstanceKey));
  }

  @Test
  public void shouldNotCreateCaseForProcessWithoutCaseMarker() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "not a case")
            .create();

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey).caseRecords())
        .isEmpty();
  }

  @Test
  public void shouldNotCreateCaseForCaseMarkedChildOfUnmarkedParent() {
    // given
    final var childId = Strings.newRandomValidBpmnId();
    final var parentId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childId))
                .endEvent()
                .done())
        .deploy();

    // when
    final var parentKey = ENGINE.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(parentKey).caseRecords())
        .isEmpty();
  }

  @Test
  public void shouldCreateOnlyOneCaseForCaseMarkedParentCallingCaseMarkedChild() {
    // given
    final var childId = Strings.newRandomValidBpmnId();
    final var parentId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childId))
                .endEvent()
                .done())
        .deploy();

    // when
    final var parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentId)
            .withVariable(TITLE_VARIABLE, "parent case")
            .create();

    // then
    final List<Record<CaseRecordValue>> caseRecords =
        RecordingExporter.records().limitToProcessInstance(parentKey).caseRecords().toList();
    assertThat(caseRecords)
        .extracting(Record::getIntent, Record::getKey, r -> r.getValue().getProcessInstanceKey())
        .containsExactly(tuple(CaseIntent.CREATED, parentKey, parentKey));
  }

  @Test
  public void shouldCreateCaseWithBusinessIdOfCreateCommand() {
    // given
    final var processId = deployUserTaskCaseProcess();
    final var businessId = Strings.newRandomValidBpmnId();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(businessId)
            .withVariable(TITLE_VARIABLE, "business case")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "business case", businessId);
  }

  @Test
  public void shouldCreateCaseWithBusinessIdOfMessageStartEvent() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    final var businessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .message(messageName)
                .userTask()
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("key-" + processId)
        .withBusinessId(businessId)
        .withVariables(Map.of(TITLE_VARIABLE, "message business case"))
        .publish();

    // then
    assertCaseCreated(
        awaitProcessInstanceKey(processId), processId, "message business case", businessId);
  }

  @Test
  public void shouldCreateCaseInTheTenantOfTheProcess() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var tenantId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .userTask()
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withTenantId(tenantId)
            .withVariable(TITLE_VARIABLE, "tenant case")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, tenantId, "tenant case", "");
  }

  @Test
  public void shouldCreateCaseInTheStepThatCreatesTheProcessInstance() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var created =
        RecordingExporter.caseRecords(CaseIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var instanceCreated =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(created.getSourceRecordPosition())
        .isEqualTo(instanceCreated.getSourceRecordPosition());
  }

  @Test
  public void shouldCreateCaseBeforeProcessInstanceIsActivated() {
    // given
    final var processId = deployUserTaskCaseProcess();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertCaseCreatedBeforeProcessInstanceIsActivated(processInstanceKey);
  }

  @Test
  public void shouldCreateCaseBeforeProcessInstanceIsActivatedWithStartInstructions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .userTask("task")
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withStartInstruction("task").create();

    // then
    assertCaseCreatedBeforeProcessInstanceIsActivated(processInstanceKey);
  }

  @Test
  public void shouldCreateCaseBeforeProcessInstanceIsActivatedByMessageStartEvent() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .message(messageName)
                .userTask()
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE.message().withName(messageName).withCorrelationKey("key-" + processId).publish();

    // then
    assertCaseCreatedBeforeProcessInstanceIsActivated(awaitProcessInstanceKey(processId));
  }

  private static String deployUserTaskCaseProcess() {
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .userTask()
                .endEvent()
                .done())
        .deploy();
    return processId;
  }

  private static long awaitProcessInstanceKey(final String processId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withBpmnProcessId(processId)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue()
        .getProcessInstanceKey();
  }

  private static void assertCaseCreated(
      final long processInstanceKey, final String processId, final String title) {
    assertCaseCreated(processInstanceKey, processId, title, "");
  }

  private static void assertCaseCreated(
      final long processInstanceKey,
      final String processId,
      final String title,
      final String businessId) {
    assertCaseCreated(
        processInstanceKey, processId, TenantOwned.DEFAULT_TENANT_IDENTIFIER, title, businessId);
  }

  private static void assertCaseCreated(
      final long processInstanceKey,
      final String processId,
      final String tenantId,
      final String title,
      final String businessId) {
    final Record<CaseRecordValue> created =
        RecordingExporter.caseRecords(CaseIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final ProcessInstanceRecordValue processInstance = awaitProcessActivating(processInstanceKey);

    assertThat(created.getKey()).isEqualTo(processInstanceKey);
    assertThat(created.getValue())
        .extracting(
            CaseRecordValue::getProcessInstanceKey,
            CaseRecordValue::getProcessDefinitionKey,
            CaseRecordValue::getBpmnProcessId,
            CaseRecordValue::getTenantId,
            CaseRecordValue::getBusinessId,
            CaseRecordValue::getTitle,
            CaseRecordValue::getStorageOrdinal)
        .containsExactly(
            processInstanceKey,
            processInstance.getProcessDefinitionKey(),
            processId,
            tenantId,
            businessId,
            title,
            processInstance.getStorageOrdinal());
    assertThat(created.getValue().getStorageOrdinal()).isEqualTo(FIXED_STORAGE_ORDINAL);
    assertThat(created.getValue().getStages()).isEmpty();
  }

  private static void assertCaseCreatedBeforeProcessInstanceIsActivated(
      final long processInstanceKey) {
    final Record<CaseRecordValue> created =
        RecordingExporter.caseRecords(CaseIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var firstActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(created.getPosition()).isLessThan(firstActivating.getPosition());
  }

  private static ProcessInstanceRecordValue awaitProcessActivating(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue();
  }
}

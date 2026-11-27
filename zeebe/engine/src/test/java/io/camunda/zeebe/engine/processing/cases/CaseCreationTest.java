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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CaseCreationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TITLE_VARIABLE = "loanTitle";
  private static final String PROCESS_NAME = "Loan application";

  /** The stages of the process that is built by {@link #caseProcess}. */
  private static final Tuple[] STAGES = {
    tuple("stage-a", "Stage A", CaseStageState.NOT_STARTED),
    tuple("stage-b", "Stage B", CaseStageState.NOT_STARTED)
  };

  private static final Tuple[] NO_STAGES = {};

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  // ---------------------------------------------------------------------------------------------
  // Creation paths
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldCreateCaseWithTitleFromCreationVariable() {
    // given
    final var processId = deployCaseProcess();

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
  public void shouldCreateCaseWithAllStagesNotStarted() {
    // given
    final var processId = deployCaseProcess();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var created = awaitCaseCreated(processInstanceKey);
    assertThat(created.getValue().getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getState)
        .containsExactly(
            tuple("stage-a", "Stage A", CaseStageState.NOT_STARTED),
            tuple("stage-b", "Stage B", CaseStageState.NOT_STARTED));
  }

  @Test
  public void shouldCreateCaseWhenCreatingWithAwaitingResult() {
    // given
    final var processId = deployCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "awaited case")
            .withResult()
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "awaited case");
  }

  @Test
  public void shouldCreateCaseWhenCreatingWithStartInstructions() {
    // given
    final var processId = deployCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "instructed case")
            .withStartInstruction("wait")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, "instructed case");
  }

  @Test
  public void shouldCreateCaseWhenMessageStartEventTriggers() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    deploy(caseProcess(processId, start -> start.message(messageName)));

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
    final var correlationKey = "key-" + processId;
    deploy(caseProcess(processId, start -> start.message(messageName)));

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
    ENGINE.job().ofInstance(firstProcessInstanceKey).withType(processId).complete();

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
    final var processDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(caseProcess(processId, start -> start.timerWithCycle("R1/PT1S")))
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // when
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    assertCaseCreated(awaitProcessInstanceKey(processId), processId, processId);
  }

  @Test
  public void shouldCreateCaseWhenSignalStartEventTriggers() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var signalName = Strings.newRandomValidBpmnId();
    deploy(caseProcess(processId, start -> start.signal(signalName)));

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
    deploy(
        caseProcess(
            processId,
            start -> start.condition(c -> c.condition("=caseCondition = \"" + processId + "\""))));

    // when
    ENGINE
        .conditionalEvaluation()
        .withVariables(Map.of("caseCondition", processId, TITLE_VARIABLE, "conditional case"))
        .evaluate();

    // then
    assertCaseCreated(awaitProcessInstanceKey(processId), processId, "conditional case");
  }

  @Test
  public void shouldCreateCaseForProcessThatCompletesStraightAway() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .endEvent()
            .done());

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "short-lived case")
            .create();

    // then
    assertCaseCreated(
        processInstanceKey,
        processId,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        "short-lived case",
        "",
        NO_STAGES);
  }

  // ---------------------------------------------------------------------------------------------
  // Ordering: same step, before activation
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldCreateCaseInTheStepThatCreatesTheProcessInstance() {
    // given
    final var processId = deployCaseProcess();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var created = awaitCaseCreated(processInstanceKey);
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
    final var processId = deployCaseProcess();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertCaseCreatedBeforeProcessInstanceIsActivated(processInstanceKey);
  }

  @Test
  public void shouldCreateCaseBeforeStartInstructionsAreActivated() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .subProcess(
                "stage",
                stage ->
                    stage
                        .name("Stage")
                        .zeebeStage()
                        .embeddedSubProcess()
                        .startEvent()
                        .userTask("task")
                        .endEvent())
            .endEvent()
            .done());

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withStartInstruction("task").create();

    // then
    final var created = awaitCaseCreated(processInstanceKey);
    final var processActivating = awaitProcessActivating(processInstanceKey);
    final var targetFlowScopeActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("stage")
            .getFirst();
    assertThat(created.getPosition())
        .isLessThan(processActivating.getPosition())
        .isLessThan(targetFlowScopeActivating.getPosition());
  }

  @Test
  public void shouldCreateCaseBeforeProcessInstanceIsActivatedByMessageStartEvent() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    deploy(caseProcess(processId, start -> start.message(messageName)));

    // when
    ENGINE.message().withName(messageName).withCorrelationKey("key-" + processId).publish();

    // then
    assertCaseCreatedBeforeProcessInstanceIsActivated(awaitProcessInstanceKey(processId));
  }

  // ---------------------------------------------------------------------------------------------
  // Title
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldUseProcessNameAsTitleWhenTitleVariableIsMissing() {
    // given
    final var processId = deployNamedCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("unrelated", "value")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, PROCESS_NAME);
  }

  @Test
  public void shouldUseProcessIdAsTitleWhenProcessHasNoName() {
    // given
    final var processId = deployCaseProcess();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("unrelated", "value")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, processId);
  }

  @Test
  public void shouldNotUseStartEventOutputMappingAsTitle() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .name(PROCESS_NAME)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .zeebeOutputExpression("\"mapped title\"", TITLE_VARIABLE)
            .userTask()
            .endEvent()
            .done());

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertCaseCreated(
        processInstanceKey,
        processId,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        PROCESS_NAME,
        "",
        NO_STAGES);
  }

  // ---------------------------------------------------------------------------------------------
  // Scope
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldNotCreateCaseForProcessWithoutCaseMarker() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done());

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "not a case")
            .create();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .withValueType(ValueType.CASE)
                .asList())
        .isEmpty();
  }

  @Test
  public void shouldNotCreateCaseForChildOfCallActivity() {
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
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(parentKey)
                .withValueType(ValueType.CASE)
                .asList())
        .extracting(
            Record::getIntent,
            record -> ((CaseRecordValue) record.getValue()).getProcessInstanceKey())
        .containsExactly(tuple(CaseIntent.CREATED, parentKey));
  }

  @Test
  public void shouldNotCreateCaseWhenCreationIsRejected() {
    // given
    final var processId = deployCaseProcess();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariable(TITLE_VARIABLE, "rejected case")
        .withStartInstruction("unknown-element")
        .expectRejection()
        .create();
    final var rejection =
        RecordingExporter.processInstanceCreationRecords()
            .onlyCommandRejections()
            .withIntent(ProcessInstanceCreationIntent.CREATE)
            .getFirst();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getPosition() == rejection.getPosition())
                .withValueType(ValueType.CASE)
                .asList())
        .isEmpty();
  }

  @Test
  public void shouldCreateCaseWithBusinessIdOfCreateCommand() {
    // given
    final var processId = deployCaseProcess();
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
    deploy(caseProcess(processId, start -> start.message(messageName)));

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
    ENGINE.deployment().withXmlResource(caseProcess(processId)).withTenantId(tenantId).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withTenantId(tenantId)
            .withVariable(TITLE_VARIABLE, "tenant case")
            .create();

    // then
    assertCaseCreated(processInstanceKey, processId, tenantId, "tenant case", "", STAGES);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private static String deployCaseProcess() {
    final var processId = Strings.newRandomValidBpmnId();
    deploy(caseProcess(processId));
    return processId;
  }

  private static String deployNamedCaseProcess() {
    final var processId = Strings.newRandomValidBpmnId();
    deploy(caseProcess(processId, process -> process.name(PROCESS_NAME), start -> start));
    return processId;
  }

  private static void deploy(final BpmnModelInstance process) {
    ENGINE.deployment().withXmlResource(process).deploy();
  }

  private static BpmnModelInstance caseProcess(final String processId) {
    return caseProcess(processId, start -> start);
  }

  private static BpmnModelInstance caseProcess(
      final String processId, final UnaryOperator<StartEventBuilder> startEvent) {
    return caseProcess(processId, process -> process, startEvent);
  }

  /**
   * Builds a case-marked process that waits at the service task {@code wait} (job type = process
   * id), then runs through the embedded sub-process stage {@code stage-a} and ends. The event
   * sub-process {@code stage-b} is the second stage.
   */
  private static BpmnModelInstance caseProcess(
      final String processId,
      final UnaryOperator<ProcessBuilder> processCustomizer,
      final UnaryOperator<StartEventBuilder> startEvent) {
    final ProcessBuilder process =
        processCustomizer.apply(Bpmn.createExecutableProcess(processId).zeebeCase(TITLE_VARIABLE));
    final var flow =
        startEvent
            .apply(process.startEvent())
            .serviceTask("wait", task -> task.zeebeJobType(processId))
            .subProcess(
                "stage-a",
                stage ->
                    stage
                        .name("Stage A")
                        .zeebeStage()
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent());
    process.eventSubProcess(
        "stage-b",
        stage ->
            stage
                .name("Stage B")
                .zeebeStage()
                .startEvent()
                .signal(Strings.newRandomValidBpmnId())
                .endEvent());
    return flow.endEvent().done();
  }

  private static long awaitProcessInstanceKey(final String processId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withBpmnProcessId(processId)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue()
        .getProcessInstanceKey();
  }

  /**
   * Returns the CASE.CREATED record of the process instance and asserts that it is the only one:
   * the case is created before the process instance is activated, so a duplicate would be written
   * before the process is activated.
   */
  private static Record<CaseRecordValue> awaitCaseCreated(final long processInstanceKey) {
    final var processActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getPosition() == processActivated.getPosition())
                .withValueType(ValueType.CASE)
                .withIntent(CaseIntent.CREATED)
                .filter(
                    record ->
                        ((CaseRecordValue) record.getValue()).getProcessInstanceKey()
                            == processInstanceKey))
        .describedAs("CASE.CREATED records of process instance %d", processInstanceKey)
        .hasSize(1);

    return RecordingExporter.caseRecords(CaseIntent.CREATED)
        .valueFilter(value -> value.getProcessInstanceKey() == processInstanceKey)
        .getFirst();
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
        processInstanceKey,
        processId,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        title,
        businessId,
        STAGES);
  }

  private static void assertCaseCreated(
      final long processInstanceKey,
      final String processId,
      final String tenantId,
      final String title,
      final String businessId,
      final Tuple... stages) {
    final Record<CaseRecordValue> created = awaitCaseCreated(processInstanceKey);
    final ProcessInstanceRecordValue processInstance =
        awaitProcessActivating(processInstanceKey).getValue();

    final long caseKey = created.getValue().getCaseKey();
    assertThat(caseKey).isNotEqualTo(processInstanceKey);
    assertThat(created.getKey()).isEqualTo(caseKey);
    assertThat(created.getValue())
        .extracting(
            CaseRecordValue::getCaseKey,
            CaseRecordValue::getState,
            CaseRecordValue::getProcessInstanceKey,
            CaseRecordValue::getProcessDefinitionKey,
            CaseRecordValue::getBpmnProcessId,
            CaseRecordValue::getTenantId,
            CaseRecordValue::getBusinessId,
            CaseRecordValue::getTitle,
            CaseRecordValue::getStorageOrdinal)
        .containsExactly(
            caseKey,
            CaseLifecycleState.ACTIVE,
            processInstanceKey,
            processInstance.getProcessDefinitionKey(),
            processId,
            tenantId,
            businessId,
            title,
            processInstance.getStorageOrdinal());
    assertThat(created.getValue().getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getState)
        .containsExactly(stages);
  }

  private static void assertCaseCreatedBeforeProcessInstanceIsActivated(
      final long processInstanceKey) {
    final var created = awaitCaseCreated(processInstanceKey);
    final var firstActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(created.getPosition()).isLessThan(firstActivating.getPosition());
  }

  private static Record<ProcessInstanceRecordValue> awaitProcessActivating(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
  }
}

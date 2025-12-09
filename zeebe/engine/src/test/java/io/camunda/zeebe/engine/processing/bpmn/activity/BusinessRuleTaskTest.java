/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class BusinessRuleTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";

  private static final String DMN_WITH_ASSERTION = "/dmn/drg-force-user-with-assertions.dmn";
  private static final String DMN_RESOURCE_WITH_NAMELESS_OUTPUTS =
      "/dmn/drg-force-user-nameless-input-outputs.dmn";

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_V2 = "/dmn/decision-table_v2.dmn";
  private static final String DMN_DECISION_TABLE_RENAMED_DRG =
      "/dmn/decision-table-with-renamed-drg.dmn";
  private static final String DMN_DECISION_TABLE_WITH_VERSION_TAG_V1 =
      "/dmn/decision-table-with-version-tag-v1.dmn";
  private static final String DMN_DECISION_TABLE_WITH_VERSION_TAG_V1_NEW =
      "/dmn/decision-table-with-version-tag-v1-new.dmn";
  private static final String DMN_DECISION_TABLE_WITH_VERSION_TAG_V2 =
      "/dmn/decision-table-with-version-tag-v2.dmn";
  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String RESULT_VARIABLE = "result";
  private static final String OUTPUT_TARGET = "output";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithBusinessRuleTask(
      final Consumer<BusinessRuleTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .businessRuleTask(TASK_ID, modifier)
        .done();
  }

  @Test
  public void shouldActivateTask() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    assertThat(taskActivating.getValue())
        .hasElementId(TASK_ID)
        .hasBpmnElementType(BpmnElementType.BUSINESS_RULE_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldActivateTaskWithCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE)))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .withTenantId(tenantId)
            .create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    assertThat(taskActivating.getValue())
        .hasElementId(TASK_ID)
        .hasBpmnElementType(BpmnElementType.BUSINESS_RULE_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(tenantId)
        .hasRootProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCompleteTask() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BUSINESS_RULE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BUSINESS_RULE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldWriteResultAsProcessInstanceVariable() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(RESULT_VARIABLE)
                .getFirst())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getScopeKey, VariableRecordValue::getValue)
        .containsExactly(processInstanceKey, "\"Jedi\"");
  }

  @Test
  public void shouldUseResultInOutputMappings() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t ->
                    t.zeebeCalledDecisionId("jedi_or_sith")
                        .zeebeResultVariable(RESULT_VARIABLE)
                        .zeebeOutputExpression(RESULT_VARIABLE, OUTPUT_TARGET)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    final long taskInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst()
            .getKey();

    // then
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(RESULT_VARIABLE)
                .getFirst())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getScopeKey, VariableRecordValue::getValue)
        .containsExactly(taskInstanceKey, "\"Jedi\"");

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(OUTPUT_TARGET)
                .getFirst())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getScopeKey, VariableRecordValue::getValue)
        .containsExactly(processInstanceKey, "\"Jedi\"");
  }

  @Test
  public void shouldWriteDecisionEvaluationEvent() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_RESOURCE)
            .withXmlResource(
                processWithBusinessRuleTask(
                    t ->
                        t.zeebeCalledDecisionId("force_user").zeebeResultVariable(RESULT_VARIABLE)))
            .deploy();

    final var deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));

    final var calledDecision = deployedDecisionsById.get("force_user");
    final var requiredDecision = deployedDecisionsById.get("jedi_or_sith");

    // when
    final Map<String, Object> variables =
        Map.ofEntries(entry("lightsaberColor", "blue"), entry("height", 182));
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables(variables).create();

    final var businessRuleTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(decisionEvaluationRecord)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DECISION_EVALUATION)
        .hasIntent(DecisionEvaluationIntent.EVALUATED)
        .hasSourceRecordPosition(businessRuleTaskActivated.getSourceRecordPosition());
    assertThat(decisionEvaluationRecord.getKey())
        .describedAs("Expect that the decision evaluation event has a key")
        .isPositive();

    final var decisionEvaluationValue = decisionEvaluationRecord.getValue();
    assertThat(decisionEvaluationValue)
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionId(calledDecision.getDecisionId())
        .hasDecisionName(calledDecision.getDecisionName())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionRequirementsKey(calledDecision.getDecisionRequirementsKey())
        .hasDecisionRequirementsId(calledDecision.getDecisionRequirementsId())
        .hasDecisionOutput("\"Obi-Wan Kenobi\"")
        .hasFailedDecisionId("")
        .hasEvaluationFailureMessage("")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(decisionEvaluationValue)
        .hasProcessDefinitionKey(businessRuleTaskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(businessRuleTaskActivated.getValue().getBpmnProcessId())
        .hasProcessInstanceKey(businessRuleTaskActivated.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(businessRuleTaskActivated.getKey())
        .hasElementId(businessRuleTaskActivated.getValue().getElementId())
        .hasRootProcessInstanceKey(processInstanceKey);

    final var evaluatedDecisions = decisionEvaluationValue.getEvaluatedDecisions();
    assertThat(evaluatedDecisions).hasSize(2);

    assertThat(evaluatedDecisions.get(0))
        .hasDecisionId("jedi_or_sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionKey(requiredDecision.getDecisionKey())
        .hasDecisionVersion(requiredDecision.getVersion())
        .hasDecisionType("DECISION_TABLE")
        .hasDecisionOutput("\"Jedi\"")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .satisfies(
            evaluatedDecision -> {
              assertThat(evaluatedDecision.getEvaluatedInputs()).hasSize(1);
              assertThat(evaluatedDecision.getEvaluatedInputs().get(0))
                  .hasInputId("Input_1")
                  .hasInputName("Lightsaber color")
                  .hasInputValue("\"blue\"");

              assertThat(evaluatedDecision.getMatchedRules()).hasSize(1);
              assertThat(evaluatedDecision.getMatchedRules().get(0))
                  .hasRuleId("DecisionRule_0zumznl")
                  .hasRuleIndex(1)
                  .satisfies(
                      matchedRule -> {
                        assertThat(matchedRule.getEvaluatedOutputs()).hasSize(1);
                        assertThat(matchedRule.getEvaluatedOutputs().get(0))
                            .hasOutputId("Output_1")
                            .hasOutputName("Jedi or Sith")
                            .hasOutputValue("\"Jedi\"");
                      });
            });

    assertThat(evaluatedDecisions.get(1))
        .hasDecisionId("force_user")
        .hasDecisionName("Which force user?")
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionType("DECISION_TABLE")
        .hasDecisionOutput("\"Obi-Wan Kenobi\"")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .satisfies(
            evaluatedDecision -> {
              assertThat(evaluatedDecision.getEvaluatedInputs()).hasSize(2);
              assertThat(evaluatedDecision.getEvaluatedInputs().get(0))
                  .hasInputId("InputClause_0qnqj25")
                  .hasInputName("Jedi or Sith")
                  .hasInputValue("\"Jedi\"");
              assertThat(evaluatedDecision.getEvaluatedInputs().get(1))
                  .hasInputId("InputClause_0k64hys")
                  .hasInputName("Body height")
                  .hasInputValue("182");

              assertThat(evaluatedDecision.getMatchedRules()).hasSize(1);
              assertThat(evaluatedDecision.getMatchedRules().get(0))
                  .hasRuleId("DecisionRule_0uin2hk")
                  .hasRuleIndex(2)
                  .satisfies(
                      matchedRule -> {
                        assertThat(matchedRule.getEvaluatedOutputs()).hasSize(1);
                        assertThat(matchedRule.getEvaluatedOutputs().get(0))
                            .hasOutputId("OutputClause_0hhe1yo")
                            .hasOutputName("Force user")
                            .hasOutputValue("\"Obi-Wan Kenobi\"");
                      });
            });
  }

  @Test
  public void shouldCallLatestDecisionVersionIfBindingTypeNotSet() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();
    final var deployment =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();
    final var latestDeployedDecision = deployment.getValue().getDecisionsMetadata().getFirst();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionId("jedi_or_sith")
        .hasDecisionKey(latestDeployedDecision.getDecisionKey())
        .hasDecisionVersion(latestDeployedDecision.getVersion());
  }

  @Test
  public void shouldCallLatestDecisionVersionForBindingTypeLatest() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t ->
                    t.zeebeCalledDecisionId("jedi_or_sith")
                        .zeebeBindingType(ZeebeBindingType.latest)
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();
    final var deployment =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();
    final var latestDeployedDecision = deployment.getValue().getDecisionsMetadata().getFirst();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionId("jedi_or_sith")
        .hasDecisionKey(latestDeployedDecision.getDecisionKey())
        .hasDecisionVersion(latestDeployedDecision.getVersion());
  }

  @Test
  public void shouldCallDecisionVersionInSameDeploymentForBindingTypeDeployment() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withXmlResource(
                processWithBusinessRuleTask(
                    t ->
                        t.zeebeCalledDecisionId("jedi_or_sith")
                            .zeebeBindingType(ZeebeBindingType.deployment)
                            .zeebeResultVariable(RESULT_VARIABLE)))
            .deploy();
    final var decisionInSameDeployment = deployment.getValue().getDecisionsMetadata().getFirst();
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionId("jedi_or_sith")
        .hasDecisionKey(decisionInSameDeployment.getDecisionKey())
        .hasDecisionVersion(decisionInSameDeployment.getVersion());
  }

  @Test
  public void shouldCallLatestDecisionVersionWithVersionTagForBindingTypeVersionTag() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1)
        .withXmlResource(
            processWithBusinessRuleTask(
                t ->
                    t.zeebeCalledDecisionId("jedi_or_sith")
                        .zeebeBindingType(ZeebeBindingType.versionTag)
                        .zeebeVersionTag("v1.0")
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1_NEW)
            .deploy();
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V2).deploy();
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();
    final var deployedDecisionV1New = deployment.getValue().getDecisionsMetadata().getFirst();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionId("jedi_or_sith")
        .hasDecisionKey(deployedDecisionV1New.getDecisionKey())
        .hasDecisionVersion(deployedDecisionV1New.getVersion());
  }

  @Test
  public void shouldCallDecisionWithDecisionIdExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                t ->
                    t.zeebeCalledDecisionIdExpression("decisionIdVariable")
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.ofEntries(
                    Map.entry("decisionIdVariable", "jedi_or_sith"),
                    Map.entry("lightsaberColor", "blue")))
            .create();

    // then
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(RESULT_VARIABLE)
                .exists())
        .as("Decision is evaluated successfully")
        .isTrue();
  }

  @Test
  public void shouldCallDecisionWithDecisionIdExpressionAndBindingTypeDeployment() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withXmlResource(
                processWithBusinessRuleTask(
                    t ->
                        t.zeebeCalledDecisionIdExpression("decisionIdVariable")
                            .zeebeBindingType(ZeebeBindingType.deployment)
                            .zeebeResultVariable(RESULT_VARIABLE)))
            .deploy();
    final var decisionInSameDeployment = deployment.getValue().getDecisionsMetadata().getFirst();
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.ofEntries(
                    Map.entry("decisionIdVariable", "jedi_or_sith"),
                    Map.entry("lightsaberColor", "blue")))
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionId("jedi_or_sith")
        .hasDecisionKey(decisionInSameDeployment.getDecisionKey())
        .hasDecisionVersion(decisionInSameDeployment.getVersion());
  }

  @Test
  public void shouldCallDecisionWithDecisionIdExpressionAndBindingTypeVersionTag() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithBusinessRuleTask(
                task ->
                    task.zeebeCalledDecisionIdExpression("decisionIdVariable")
                        .zeebeBindingType(ZeebeBindingType.versionTag)
                        .zeebeVersionTag("v1.0")
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_VERSION_TAG_V1)
            .deploy();
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();
    final var deployedDecisionV1 = deployment.getValue().getDecisionsMetadata().getFirst();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.ofEntries(
                    Map.entry("decisionIdVariable", "jedi_or_sith"),
                    Map.entry("lightsaberColor", "blue")))
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionId("jedi_or_sith")
        .hasDecisionKey(deployedDecisionV1.getDecisionKey())
        .hasDecisionVersion(deployedDecisionV1.getVersion());
  }

  @Test
  public void shouldWriteDecisionEvaluationEventIfEvaluationFailed() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_WITH_ASSERTION)
            .withXmlResource(
                processWithBusinessRuleTask(
                    t ->
                        t.zeebeCalledDecisionId("force_user").zeebeResultVariable(RESULT_VARIABLE)))
            .deploy();

    final var deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));

    final var calledDecision = deployedDecisionsById.get("force_user");
    final var requiredDecision = deployedDecisionsById.get("jedi_or_sith");

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var businessRuleTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(decisionEvaluationRecord)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DECISION_EVALUATION)
        .hasIntent(DecisionEvaluationIntent.FAILED)
        .hasSourceRecordPosition(businessRuleTaskActivating.getSourceRecordPosition());
    assertThat(decisionEvaluationRecord.getKey())
        .describedAs("Expect that the decision evaluation event has a key")
        .isPositive();

    final var decisionEvaluationValue = decisionEvaluationRecord.getValue();
    assertThat(decisionEvaluationValue)
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionId(calledDecision.getDecisionId())
        .hasDecisionName(calledDecision.getDecisionName())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionRequirementsKey(calledDecision.getDecisionRequirementsKey())
        .hasDecisionRequirementsId(calledDecision.getDecisionRequirementsId())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasDecisionOutput("null")
        .hasFailedDecisionId("jedi_or_sith")
        .hasEvaluationFailureMessage(
            """
            Expected to evaluate decision 'force_user', \
            but Assertion failure on evaluate the expression \
            'assert(lightsaberColor, lightsaberColor != null)': The condition is not fulfilled""");

    assertThat(decisionEvaluationValue)
        .hasProcessDefinitionKey(businessRuleTaskActivating.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(businessRuleTaskActivating.getValue().getBpmnProcessId())
        .hasProcessInstanceKey(businessRuleTaskActivating.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(businessRuleTaskActivating.getKey())
        .hasElementId(businessRuleTaskActivating.getValue().getElementId());

    final var evaluatedDecisions = decisionEvaluationValue.getEvaluatedDecisions();
    assertThat(evaluatedDecisions).hasSize(1);

    assertThat(evaluatedDecisions.get(0))
        .hasDecisionId("jedi_or_sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionKey(requiredDecision.getDecisionKey())
        .hasDecisionVersion(requiredDecision.getVersion())
        .hasDecisionType("DECISION_TABLE")
        .hasDecisionOutput("null")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .satisfies(
            evaluatedDecision -> {
              assertThat(evaluatedDecision.getEvaluatedInputs()).isEmpty();
              assertThat(evaluatedDecision.getMatchedRules()).isEmpty();
            });
  }

  /**
   * Names are not mandatory for the Outputs of a decision table. In the case that they are missing
   * from the decision model, the decision should still be evaluated and the evaluated decision
   * result should be written with all information that is present. Note that this is not the case
   * for Inputs, as these always have an expression which is used in case the label is undefined.
   * See https://github.com/camunda-cloud/zeebe/issues/8909
   */
  @Test
  public void shouldWriteDecisionEvaluationEventIfInputOutputNamesAreNull() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE_WITH_NAMELESS_OUTPUTS)
        .withXmlResource(
            processWithBusinessRuleTask(
                t -> t.zeebeCalledDecisionId("force_user").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.ofEntries(entry("lightsaberColor", "blue"), entry("height", 182)))
            .create();

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(decisionEvaluationRecord.getValue().getEvaluatedDecisions())
        .isNotEmpty()
        .flatMap(EvaluatedDecisionValue::getMatchedRules)
        .isNotEmpty()
        .flatMap(MatchedRuleValue::getEvaluatedOutputs)
        .isNotEmpty()
        .extracting(EvaluatedOutputValue::getOutputName)
        .describedAs("Expect that evaluated output's name is empty string")
        .containsOnly("");
  }

  /**
   * Regression test for https://github.com/camunda/camunda/issues/9272. An exception occurred if
   * two DRGs were deployed with a different id but the same decision id.
   */
  @Test
  public void shouldEvaluateDecisionIfMultipleDrgsWithSameDecisionId() {
    // given
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_RENAMED_DRG).deploy();

    final var deploymentCreated =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withXmlResource(
                processWithBusinessRuleTask(
                    t ->
                        t.zeebeCalledDecisionId("jedi_or_sith")
                            .zeebeResultVariable(RESULT_VARIABLE)))
            .deploy();

    final var lastDeployment = deploymentCreated.getValue();
    final var lastDeployedDecisionRequirements =
        lastDeployment.getDecisionRequirementsMetadata().get(0);
    final var lastDeployedDecision = lastDeployment.getDecisionsMetadata().get(0);

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.ofEntries(entry("lightsaberColor", "blue")))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BUSINESS_RULE_TASK))
        .describedAs("expected the business rule task to be completed")
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);

    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(decisionEvaluationRecord.getValue())
        .hasDecisionKey(lastDeployedDecision.getDecisionKey())
        .hasDecisionRequirementsKey(lastDeployedDecisionRequirements.getDecisionRequirementsKey())
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldEvaluateBusinessRuleTaskWithSynthesizedRuleIdWhenMissing() {
    // given
    final String decisionId = "shippingDecision";
    final String resultVar = "shippingType";

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .businessRuleTask(
                TASK_ID, t -> t.zeebeCalledDecisionId(decisionId).zeebeResultVariable(resultVar))
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/decision-table-with-missing-ruleId.dmn")
        .withXmlResource(process)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("amount", 300).create();

    // then (decision evaluation event exists and is successful)
    final var decisionEvaluationRecordValue =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
            .withProcessInstanceKey(processInstanceKey)
            .withDecisionId(decisionId)
            .getFirst()
            .getValue();

    assertThat(decisionEvaluationRecordValue)
        .hasDecisionId(decisionId)
        .hasDecisionOutput("\"EXPRESS\"")
        .hasDecisionVersion(1);

    // verify matched rule: index 2 and synthesized ruleId
    final var evaluatedDecision =
        decisionEvaluationRecordValue.getEvaluatedDecisions().stream()
            .filter(d -> decisionId.equals(d.getDecisionId()))
            .findFirst()
            .orElseThrow();

    assertThat(evaluatedDecision.getMatchedRules())
        .singleElement()
        .satisfies(
            matchedRule -> {
              assertThat(matchedRule.getRuleIndex()).isEqualTo(2);
              assertThat(matchedRule.getRuleId())
                  .isEqualTo("ZB_SYNTH_RULE_ID_shippingDecision_v1_r2");
            });

    // result variable written by the business rule task
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .withName(resultVar)
                .getFirst()
                .getValue())
        .hasValue("\"EXPRESS\"");
  }
}

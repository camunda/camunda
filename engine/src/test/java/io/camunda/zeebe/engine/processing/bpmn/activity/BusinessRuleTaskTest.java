/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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
  private static final String DMN_RESOURCE_WITH_NAMELESS_INPUTS =
      "/dmn/drg-force-user-nameless-inputs.dmn";
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
        .hasProcessInstanceKey(processInstanceKey);
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
        .hasEvaluationFailureMessage("");

    assertThat(decisionEvaluationValue)
        .hasProcessDefinitionKey(businessRuleTaskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(businessRuleTaskActivated.getValue().getBpmnProcessId())
        .hasProcessInstanceKey(businessRuleTaskActivated.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(businessRuleTaskActivated.getKey())
        .hasElementId(businessRuleTaskActivated.getValue().getElementId());

    final var evaluatedDecisions = decisionEvaluationValue.getEvaluatedDecisions();
    assertThat(evaluatedDecisions).hasSize(2);

    assertThat(evaluatedDecisions.get(0))
        .hasDecisionId("jedi_or_sith")
        .hasDecisionName("Jedi or Sith")
        .hasDecisionKey(requiredDecision.getDecisionKey())
        .hasDecisionVersion(requiredDecision.getVersion())
        .hasDecisionType("DECISION_TABLE")
        .hasDecisionOutput("\"Jedi\"")
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
                            .hasOutputName("jedi_or_sith")
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
                            .hasOutputName("force_user")
                            .hasOutputValue("\"Obi-Wan Kenobi\"");
                      });
            });
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
  public void shouldWriteDecisionEvaluationEventIfEvaluationFailed() {
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
        .hasDecisionOutput("null")
        .hasFailedDecisionId("jedi_or_sith")
        .hasEvaluationFailureMessage(
            """
            Expected to evaluate decision 'force_user', \
            but failed to evaluate expression 'lightsaberColor': \
            no variable found for name 'lightsaberColor'\
            """);

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
        .satisfies(
            evaluatedDecision -> {
              assertThat(evaluatedDecision.getEvaluatedInputs()).isEmpty();
              assertThat(evaluatedDecision.getMatchedRules()).isEmpty();
            });
  }

  /**
   * Names are not mandatory for the Inputs and Outputs of a decision table. In the case that they
   * are missing from the decision model, the decision should still be evaluated and the evaluated
   * decision result should be written with all information that is present. See
   * https://github.com/camunda-cloud/zeebe/issues/8909
   */
  @Test
  public void shouldWriteDecisionEvaluationEventIfInputOutputNamesAreNull() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE_WITH_NAMELESS_INPUTS)
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
        .allSatisfy(
            evaluatedDecision -> {
              assertThat(evaluatedDecision.getEvaluatedInputs())
                  .isNotEmpty()
                  .describedAs("Expect that evaluated input's name is empty string")
                  .allSatisfy(input -> assertThat(input).hasInputName(""));

              assertThat(evaluatedDecision.getMatchedRules())
                  .isNotEmpty()
                  .allSatisfy(
                      matchedRule ->
                          assertThat(matchedRule.getEvaluatedOutputs())
                              .isNotEmpty()
                              .describedAs("Expect that evaluated output's name is empty string")
                              .allSatisfy(output -> assertThat(output).hasOutputName("")));
            });
  }
}

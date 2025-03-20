/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareBusinessRuleTaskTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_WITH_ASSERTION =
      "/dmn/decision-table-with-assertions.dmn";

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String RESULT_VARIABLE = "result";

  private static final String DECISION_ID = "jedi_or_sith";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private final String tenantOne = "foo";
  private final String tenantTwo = "bar";

  private Map<String, DecisionRecordValue> deployedDecisionsById;

  private static BpmnModelInstance processWithBusinessRuleTask() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .businessRuleTask(
            TASK_ID, t -> t.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE))
        .done();
  }

  @Test
  public void shouldActivateBusinessRuleTask() {
    // given
    final var process = processWithBusinessRuleTask();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withTenantId(tenantOne)
            .deploy();

    deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));

    ENGINE.deployment().withXmlResource("process.bpmn", process).withTenantId(tenantTwo).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTenantId(tenantOne)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
                .withTenantId(tenantOne)
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
        .hasTenantId(tenantOne);
  }

  @Test
  public void shouldWriteDecisionEvaluationEvent() {
    // given
    final var process = processWithBusinessRuleTask();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withTenantId(tenantOne)
            .deploy();

    deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));

    ENGINE.deployment().withXmlResource("process.bpmn", process).withTenantId(tenantTwo).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTenantId(tenantOne)
            .withVariable("lightsaberColor", "blue")
            .create();

    final var calledDecision = deployedDecisionsById.get(DECISION_ID);

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(tenantOne)
            .getFirst();

    assertThat(decisionEvaluationRecord)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DECISION_EVALUATION)
        .hasIntent(DecisionEvaluationIntent.EVALUATED);

    final var decisionEvaluationValue = decisionEvaluationRecord.getValue();
    assertThat(decisionEvaluationValue)
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionId(calledDecision.getDecisionId())
        .hasDecisionName(calledDecision.getDecisionName())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionRequirementsKey(calledDecision.getDecisionRequirementsKey())
        .hasDecisionRequirementsId(calledDecision.getDecisionRequirementsId())
        .hasTenantId(tenantOne);

    final var evaluatedDecision = decisionEvaluationValue.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecision).hasTenantId(tenantOne);
  }

  @Test
  public void shouldWriteDecisionEvaluationEventIfEvaluationFailed() {
    // given
    final var process = processWithBusinessRuleTask();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_WITH_ASSERTION)
            .withTenantId(tenantOne)
            .deploy();

    deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));

    ENGINE.deployment().withXmlResource("process.bpmn", process).withTenantId(tenantTwo).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantOne).create();

    final var calledDecision = deployedDecisionsById.get(DECISION_ID);

    // then
    final var decisionEvaluationRecord =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(decisionEvaluationRecord)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DECISION_EVALUATION)
        .hasIntent(DecisionEvaluationIntent.FAILED);

    final var decisionEvaluationValue = decisionEvaluationRecord.getValue();
    assertThat(decisionEvaluationValue)
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionId(calledDecision.getDecisionId())
        .hasDecisionName(calledDecision.getDecisionName())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionRequirementsKey(calledDecision.getDecisionRequirementsKey())
        .hasDecisionRequirementsId(calledDecision.getDecisionRequirementsId())
        .hasTenantId(tenantOne)
        .hasEvaluationFailureMessage(
            """
            Expected to evaluate decision 'jedi_or_sith', but \
            Assertion failure on evaluate the expression \
            'assert(lightsaberColor, lightsaberColor != null)': The condition is not fulfilled""");

    final var evaluatedDecision = decisionEvaluationValue.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecision).hasTenantId(tenantOne);
  }

  @Test
  public void shouldNotActivateBusinessRuleTask() {
    // given
    final var process = processWithBusinessRuleTask();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withXmlClasspathResource(DMN_DECISION_TABLE)
            .withTenantId(tenantOne)
            .deploy();

    deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));

    ENGINE.deployment().withXmlResource("process.bpmn", process).withTenantId(tenantTwo).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTenantId(tenantTwo)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(tenantTwo)
                .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .isEmpty();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.CALLED_DECISION_ERROR)
        .hasErrorMessage(
            "Expected to evaluate decision '"
                + DECISION_ID
                + "', but no decision found for id '"
                + DECISION_ID
                + "'");
  }
}

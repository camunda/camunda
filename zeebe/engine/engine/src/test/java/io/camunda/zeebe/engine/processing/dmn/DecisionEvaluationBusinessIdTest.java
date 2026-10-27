/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class DecisionEvaluationBusinessIdTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String PROCESS_ID = "process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String RESULT_VARIABLE = "result";
  private static final String BUSINESS_ID = "order-123";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithBusinessRuleTask() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .businessRuleTask(
            "task",
            t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance parentProcessWithCallActivity() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .callActivity("call", c -> c.zeebeProcessId(CHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance childProcessWithBusinessRuleTask() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .businessRuleTask(
            "child-task",
            t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE))
        .endEvent()
        .done();
  }

  @Test
  public void shouldInheritBusinessIdFromOwningProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource(processWithBusinessRuleTask())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId(BUSINESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final Record<DecisionEvaluationRecordValue> decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(decisionEvaluated.getValue()).hasBusinessId(BUSINESS_ID);
  }

  @Test
  public void shouldInheritBusinessIdFromOwningChildProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithBusinessRuleTask())
        .deploy();

    // when
    final long parentProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId(BUSINESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then - the decision is evaluated in the child instance, which inherited the business ID
    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final Record<DecisionEvaluationRecordValue> decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
            .withProcessInstanceKey(childProcessInstance.getKey())
            .getFirst();

    Assertions.assertThat(decisionEvaluated.getValue()).hasBusinessId(BUSINESS_ID);
  }

  @Test
  public void shouldHaveEmptyBusinessIdWhenProcessInstanceHasNone() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource(processWithBusinessRuleTask())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final Record<DecisionEvaluationRecordValue> decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(decisionEvaluated.getValue()).hasBusinessId("");
  }

  @Test
  public void shouldHaveEmptyBusinessIdForStandaloneEvaluation() {
    // given
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // when - the decision is evaluated ad-hoc, outside of any process instance
    final Record<DecisionEvaluationRecordValue> decisionEvaluated =
        ENGINE
            .decision()
            .ofDecisionId("jedi_or_sith")
            .withVariable("lightsaberColor", "blue")
            .evaluate();

    // then
    Assertions.assertThat(decisionEvaluated.getValue()).hasBusinessId("");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import java.util.Set;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class DecisionEvaluationRootProcessInstanceKeyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String PROCESS_ID = "process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String GRANDCHILD_PROCESS_ID = "grandchild-process";
  private static final String RESULT_VARIABLE = "result";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance simpleProcessWithBusinessRuleTask() {
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

  private static BpmnModelInstance childProcessWithCallActivity() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .callActivity("child-call", c -> c.zeebeProcessId(GRANDCHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance grandchildProcessWithBusinessRuleTask() {
    return Bpmn.createExecutableProcess(GRANDCHILD_PROCESS_ID)
        .startEvent()
        .businessRuleTask(
            "grandchild-task",
            t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable(RESULT_VARIABLE))
        .endEvent()
        .done();
  }

  @Test
  public void shouldSetRootProcessInstanceKeyToProcessInstanceKeyForSimpleProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource(simpleProcessWithBusinessRuleTask())
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

    Assertions.assertThat(decisionEvaluated.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasRootProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldSetRootProcessInstanceKeyToTopLevelParentForNestedCallActivities() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithCallActivity())
        .withXmlResource("grandchild.bpmn", grandchildProcessWithBusinessRuleTask())
        .deploy();

    // when
    final long rootProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then - get the child process instance
    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(rootProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long childProcessInstanceKey = childProcessInstance.getKey();

    // get the grandchild process instance
    final Record<ProcessInstanceRecordValue> grandchildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(childProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long grandchildProcessInstanceKey = grandchildProcessInstance.getKey();

    // Verify all three process instances are different
    assertThat(
            Set.of(rootProcessInstanceKey, childProcessInstanceKey, grandchildProcessInstanceKey))
        .hasSize(3);

    // get the decision evaluation in the grandchild process
    final Record<DecisionEvaluationRecordValue> decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
            .withProcessInstanceKey(grandchildProcessInstanceKey)
            .getFirst();

    Assertions.assertThat(decisionEvaluated.getValue())
        .hasProcessInstanceKey(grandchildProcessInstanceKey)
        .hasRootProcessInstanceKey(rootProcessInstanceKey);
  }

  @Test
  public void shouldPropagateRootProcessInstanceKeyThroughAllDecisionEvaluationIntents() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(DMN_DECISION_TABLE)
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithBusinessRuleTask())
        .deploy();

    // when
    final long rootProcessInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(rootProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long childProcessInstanceKey = childProcessInstance.getKey();

    // then - verify all decision evaluation events have the correct rootProcessInstanceKey
    assertThat(
            RecordingExporter.decisionEvaluationRecords()
                .withProcessInstanceKey(childProcessInstanceKey)
                .limit(1))
        .extracting(Record::getIntent, r -> r.getValue().getRootProcessInstanceKey())
        .containsExactly(tuple(DecisionEvaluationIntent.EVALUATED, rootProcessInstanceKey));
  }
}

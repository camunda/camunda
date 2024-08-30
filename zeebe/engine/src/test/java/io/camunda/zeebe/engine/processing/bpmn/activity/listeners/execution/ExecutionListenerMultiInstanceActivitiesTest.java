/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution;

import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.END_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.PROCESS_ID;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.SERVICE_TASK_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.START_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.SUB_PROCESS_ID;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerMultiInstanceActivitiesTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final Collection<Integer> INPUT_COLLECTION = List.of(1, 2, 3, 4);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldInvokeExecutionListenerAroundSequentialMultiInstanceActivity() {
    // given
    final long processInstanceKey = createProcessInstance(ENGINE, buildMainProcessModel(true));

    // when: simulate execution for each instance in the multi-instance activity
    executeMultiInstanceActivity(processInstanceKey);

    // then
    assertExecutionListenerEvents(processInstanceKey);
  }

  @Test
  public void shouldInvokeExecutionListenerAroundParallelMultiInstanceActivity() {
    // given
    final long processInstanceKey = createProcessInstance(ENGINE, buildMainProcessModel(false));

    // when: simulate execution for each instance in the multi-instance activity
    executeMultiInstanceActivity(processInstanceKey);

    // then
    assertExecutionListenerEvents(processInstanceKey);
  }

  private static void createChildProcess() {
    final var childProcess =
        Bpmn.createExecutableProcess(SUB_PROCESS_ID).startEvent().manualTask().endEvent().done();
    ENGINE.deployment().withXmlResource("child.xml", childProcess).deploy();
  }

  private BpmnModelInstance buildMainProcessModel(boolean sequential) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            "service_task",
            t ->
                t.zeebeJobType(SERVICE_TASK_TYPE)
                    .zeebeStartExecutionListener(START_EL_TYPE)
                    .zeebeEndExecutionListener(END_EL_TYPE))
        .multiInstance(
            m -> {
              if (sequential) {
                m.sequential();
              } else {
                m.parallel();
              }
              m.zeebeInputCollectionExpression(INPUT_COLLECTION.toString());
            })
        .endEvent()
        .done();
  }

  private void executeMultiInstanceActivity(final long processInstanceKey) {
    for (int i = 0; i < INPUT_COLLECTION.size(); i++) {
      completeJobByType(processInstanceKey, START_EL_TYPE, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
      completeJobByType(processInstanceKey, END_EL_TYPE, i);
    }
  }

  private void assertExecutionListenerEvents(final long processInstanceKey) {
    final var elementInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("service_task")
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(INPUT_COLLECTION.size())
            .map(Record::getKey)
            .toList();

    final var actual =
        RecordingExporter.records()
            .betweenProcessInstance(processInstanceKey)
            .withValueType(ValueType.JOB)
            .withIntent(JobIntent.COMPLETED)
            .map(Record::getValue)
            .map(JobRecordValue.class::cast)
            .filter(r -> r.getJobKind() == JobKind.EXECUTION_LISTENER)
            .toList();

    final var expected = new ArrayList<Tuple>();
    for (final Long elementInstanceKey : elementInstanceKeys) {
      expected.add(tuple(START_EL_TYPE, elementInstanceKey));
      expected.add(tuple(END_EL_TYPE, elementInstanceKey));
    }

    assertThat(actual)
        .extracting(JobRecordValue::getType, JobRecordValue::getElementInstanceKey)
        .hasSize(INPUT_COLLECTION.size() * 2)
        .containsExactlyElementsOf(expected);
  }

  private static void completeJobByType(
      final long processInstanceKey, final String taskType, final int index) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(taskType)
            .skip(index)
            .getFirst()
            .getKey();
    ENGINE.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }
}

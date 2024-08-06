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
import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ExecutionListenerMultiInstanceActivitiesTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final Collection<Integer> INPUT_COLLECTION = List.of(1, 2, 3, 4);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public MultiInstanceTestScenario scenario;

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {
            MultiInstanceTestScenario.of(
                "service_task",
                BpmnElementType.SERVICE_TASK,
                se -> se.serviceTask("service_task").zeebeJobType(SERVICE_TASK_TYPE),
                (pik, index) -> completeJobByType(pik, SERVICE_TASK_TYPE, index))
          },
          {
            MultiInstanceTestScenario.of(
                "receive_task",
                BpmnElementType.RECEIVE_TASK,
                se ->
                    se.receiveTask("receive_task")
                        .message(mb -> mb.name("msg").zeebeCorrelationKey("=\"id-123\"")),
                (pik, index) ->
                    ENGINE.message().withName("msg").withCorrelationKey("id-123").publish())
          },
          {
            MultiInstanceTestScenario.of(
                "manual_task", BpmnElementType.MANUAL_TASK, se -> se.manualTask("manual_task"))
          },
          {
            MultiInstanceTestScenario.of(
                "user_task",
                BpmnElementType.USER_TASK,
                se -> se.userTask("user_task"),
                (pik, index) -> completeJobByType(pik, "io.camunda.zeebe:userTask", index))
          },
          {
            MultiInstanceTestScenario.of(
                "sub_process",
                BpmnElementType.SUB_PROCESS,
                se ->
                    se.subProcess(
                        "sub_process",
                        s -> s.embeddedSubProcess().startEvent().manualTask().endEvent()))
          },
          {
            MultiInstanceTestScenario.of(
                "call_activity",
                BpmnElementType.CALL_ACTIVITY,
                se -> se.callActivity("call_activity", s -> s.zeebeProcessId(SUB_PROCESS_ID)))
          }
        });
  }

  @Test
  public void shouldInvokeExecutionListenerAroundSequentialMultiInstanceActivity() {
    // given
    // create a child process if call activity
    if (scenario.elementType == BpmnElementType.CALL_ACTIVITY) {
      createChildProcess();
    }
    final long processInstanceKey = createProcessInstance(ENGINE, buildMainProcessModel(true));

    // when: simulate execution for each instance in the multi-instance activity
    executeMultiInstanceActivity(processInstanceKey);

    // then
    assertExecutionListenerEvents(processInstanceKey);
  }

  @Test
  public void shouldInvokeExecutionListenerAroundParallelMultiInstanceActivity() {
    // given
    // create a child process if call activity
    if (scenario.elementType == BpmnElementType.CALL_ACTIVITY) {
      createChildProcess();
    }
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
    return scenario
        .activityBuilder
        .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
        .zeebeStartExecutionListener(START_EL_TYPE)
        .zeebeEndExecutionListener(END_EL_TYPE)
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
      scenario.multiInstanceActivityProcessor.accept(processInstanceKey, i);
      completeJobByType(processInstanceKey, END_EL_TYPE, i);
    }
  }

  private void assertExecutionListenerEvents(final long processInstanceKey) {
    final var elementInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(scenario.name)
            .withElementType(scenario.elementType)
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

  private record MultiInstanceTestScenario(
      String name,
      BpmnElementType elementType,
      Function<StartEventBuilder, AbstractActivityBuilder<?, ?>> activityBuilder,
      BiConsumer<Long, Integer> multiInstanceActivityProcessor) {

    @Override
    public String toString() {
      return name;
    }

    private static MultiInstanceTestScenario of(
        final String name,
        final BpmnElementType elementType,
        final Function<StartEventBuilder, AbstractActivityBuilder<?, ?>> activityBuilder,
        final BiConsumer<Long, Integer> multiInstanceActivityProcessor) {
      return new MultiInstanceTestScenario(
          name, elementType, activityBuilder, multiInstanceActivityProcessor);
    }

    private static MultiInstanceTestScenario of(
        final String name,
        final BpmnElementType elementType,
        final Function<StartEventBuilder, AbstractActivityBuilder<?, ?>> activityBuilder) {
      return new MultiInstanceTestScenario(
          name, elementType, activityBuilder, (ignore1, ignore2) -> {});
    }
  }
}

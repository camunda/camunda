/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.MultiInstanceLoopCharacteristicsBuilder;
import io.camunda.zeebe.model.bpmn.builder.zeebe.MessageBuilder;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class MultiInstanceActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";

  private static final String INPUT_COLLECTION_EXPRESSION = "items";
  private static final String INPUT_ELEMENT_VARIABLE = "item";
  private static final List<Integer> INPUT_COLLECTION = List.of(10, 20, 30);
  private static final String OUTPUT_COLLECTION_VARIABLE = "results";
  private static final String OUTPUT_ELEMENT_EXPRESSION = "result";
  private static final List<Integer> OUTPUT_COLLECTION = List.of(11, 22, 33);
  private static final String COMPLETION_CONDITION_EXPRESSION = "=x";

  private static final String MESSAGE_CORRELATION_KEY_VARIABLE = "correlationKey";
  private static final String MESSAGE_CORRELATION_KEY = "key-123";
  private static final String MESSAGE_NAME = "message";

  private static final Consumer<MultiInstanceLoopCharacteristicsBuilder> INPUT_VARIABLE_BUILDER =
      multiInstance(
          m ->
              m.zeebeInputCollectionExpression(INPUT_COLLECTION_EXPRESSION)
                  .zeebeInputElement(INPUT_ELEMENT_VARIABLE)
                  .zeebeOutputElementExpression(OUTPUT_ELEMENT_EXPRESSION)
                  .zeebeOutputCollection(OUTPUT_COLLECTION_VARIABLE));

  private static final Consumer<MessageBuilder> MESSAGE_BUILDER =
      m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(MESSAGE_CORRELATION_KEY_VARIABLE);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Parameterized.Parameter(0)
  public String loopCharacteristics;

  @Parameterized.Parameter(1)
  public Consumer<MultiInstanceLoopCharacteristicsBuilder> miBuilder;

  @Parameterized.Parameter(2)
  public List<Tuple> expectedLifecycle;

  private String jobType;

  private BpmnModelInstance process(
      final Consumer<MultiInstanceLoopCharacteristicsBuilder> builder) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            ELEMENT_ID,
            t -> t.zeebeJobType(jobType).multiInstance(INPUT_VARIABLE_BUILDER.andThen(builder)))
        .endEvent()
        .done();
  }

  @Parameterized.Parameters(name = "{0} multi-instance")
  public static Object[][] parameters() {
    return new Object[][] {
      {"parallel", multiInstance(m -> m.parallel()), parallelLifecycle()},
      {"sequential", multiInstance(m -> m.sequential()), sequentialLifecycle()},
    };
  }

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  private static Consumer<MultiInstanceLoopCharacteristicsBuilder> multiInstance(
      final Consumer<MultiInstanceLoopCharacteristicsBuilder> builder) {
    return builder;
  }

  private static List<Tuple> parallelLifecycle() {
    return Arrays.asList(
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static List<Tuple> sequentialLifecycle() {
    return Arrays.asList(
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldActivateActivitiesWithLoopCharacteristics() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(expectedLifecycle);
  }

  @Test
  public void shouldActivateActivitiesForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateOneJobForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(INPUT_COLLECTION.size()))
        .hasSize(INPUT_COLLECTION.size())
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getElementId)
        .containsOnly(ELEMENT_ID);
  }

  @Test
  public void shouldCompleteBodyWhenAllJobsAreCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteBodyWhenCompleteConditionEvaluateTrue() {
    // given
    final Map<String, Object> variables =
        Map.of(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION, "x", true);
    ENGINE
        .deployment()
        .withXmlResource(
            process(miBuilder.andThen(m -> m.completionCondition(COMPLETION_CONDITION_EXPRESSION))))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables(variables).create();

    final int completedJobs = 1;
    completeJobs(processInstanceKey, completedJobs);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED));

    if ("parallel".equals(loopCharacteristics)) {
      // after 1 has completed, the others must be terminated
      final var expectedNumberOfTerminatedServiceTasks = INPUT_COLLECTION.size() - completedJobs;
      assertThat(
              RecordingExporter.records()
                  .limitToProcessInstance(processInstanceKey)
                  .processInstanceRecords()
                  .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                  .withElementType(BpmnElementType.SERVICE_TASK)
                  .count())
          .describedAs("all non-completed service tasks have terminated")
          .isEqualTo(expectedNumberOfTerminatedServiceTasks);
    } else {
      assertThat(
              RecordingExporter.records()
                  .limitToProcessInstance(processInstanceKey)
                  .processInstanceRecords()
                  .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                  .withElementType(BpmnElementType.SERVICE_TASK)
                  .count())
          .describedAs("only 1 out of 3 sequential service tasks has activated")
          .isEqualTo(1);
    }
  }

  @Test
  public void shouldCompleteBodyWhenLastInstanceCompleteConditionEvaluateTrue() {
    // given
    final Map<String, Object> variables =
        Map.of(INPUT_COLLECTION_EXPRESSION, List.of(10, 20), "x", false);
    ENGINE
        .deployment()
        .withXmlResource(
            process(miBuilder.andThen(m -> m.completionCondition(COMPLETION_CONDITION_EXPRESSION))))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables(variables).create();

    final int completedJobs = 1;
    completeJobs(processInstanceKey, completedJobs);

    // update the variable `x`
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Maps.of(entry("x", true))).update();

    // and complete the last instance
    completeJobs(processInstanceKey, completedJobs);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteBodyWhenCompleteConditionAccessInputDataItemEvaluateTrue() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(miBuilder.andThen(m -> m.completionCondition("= item = 20"))))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    final int completedJobs = 2;
    completeJobs(processInstanceKey, completedJobs);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED));

    if ("parallel".equals(loopCharacteristics)) {
      // after 2 has completed, the others must be terminated
      final var expectedNumberOfTerminatedServiceTasks = INPUT_COLLECTION.size() - completedJobs;
      assertThat(
              RecordingExporter.records()
                  .limitToProcessInstance(processInstanceKey)
                  .processInstanceRecords()
                  .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                  .withElementType(BpmnElementType.SERVICE_TASK)
                  .count())
          .describedAs("all non-completed service tasks have terminated")
          .isEqualTo(expectedNumberOfTerminatedServiceTasks);
    } else {
      assertThat(
              RecordingExporter.records()
                  .limitToProcessInstance(processInstanceKey)
                  .processInstanceRecords()
                  .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                  .withElementType(BpmnElementType.SERVICE_TASK)
                  .count())
          .describedAs("only 2 out of 3 sequential service tasks has activated")
          .isEqualTo(2);
    }
  }

  @Test
  public void shouldCompleteBodyWhenCompleteConditionEvaluateFalse() {
    // given
    final Map<String, Object> variables =
        Map.of(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION, "x", false);
    ENGINE
        .deployment()
        .withXmlResource(
            process(miBuilder.andThen(m -> m.completionCondition(COMPLETION_CONDITION_EXPRESSION))))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables(variables).create();

    final int completedJobs = 3;
    completeJobs(processInstanceKey, completedJobs);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldGoThroughMultiInstanceActivity() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldSetInputElementVariable() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).limit(3))
        .flatExtracting(r -> r.getValue().getJobs())
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT_VARIABLE))
        .containsExactlyElementsOf(INPUT_COLLECTION);

    final List<String> jsonInputCollection =
        INPUT_COLLECTION.stream().map(JsonUtil::toJson).collect(Collectors.toList());

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(INPUT_ELEMENT_VARIABLE)
                .limit(3))
        .extracting(r -> r.getValue().getValue())
        .containsExactlyElementsOf(jsonInputCollection);
  }

  @Test
  public void shouldNotPropagateInputElementVariable() {
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(INPUT_ELEMENT_VARIABLE);
  }

  @Test
  public void shouldCancelJobsOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, 1);

    // when
    final Record<JobRecordValue> createdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .hasSize(1)
        .extracting(Record::getKey)
        .containsExactly(createdJob.getKey());
  }

  @Test
  public void shouldTerminateInstancesOnTerminatingBody() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    final int completedJobs = INPUT_COLLECTION.size() - 1;
    completeJobs(processInstanceKey, completedJobs);

    // when
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(ELEMENT_ID)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .skip(completedJobs)
        .exists();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldSkipIfCollectionIsEmpty() {
    // when
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, Collections.emptyList())
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(ELEMENT_ID)
                .limit(6))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldIgnoreInputElementVariableIfNotDefined() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                miBuilder.andThen(
                    m ->
                        m.zeebeInputCollectionExpression(INPUT_COLLECTION_EXPRESSION)
                            .zeebeInputElement(null))))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
                .withType(jobType)
                .limit(INPUT_COLLECTION.size()))
        .flatExtracting(r -> r.getValue().getJobs())
        .flatExtracting(j -> j.getVariables().keySet())
        .doesNotContain(INPUT_ELEMENT_VARIABLE);
  }

  @Test
  public void shouldIterateOverNestedInputCollection() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                miBuilder.andThen(
                    m ->
                        m.zeebeInputCollectionExpression("nested." + INPUT_COLLECTION_EXPRESSION))))
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(
                "nested", Collections.singletonMap(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(INPUT_COLLECTION.size())
        .exists();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
                .withType(jobType)
                .limit(INPUT_COLLECTION.size()))
        .flatExtracting(r -> r.getValue().getJobs())
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT_VARIABLE))
        .containsExactlyElementsOf(INPUT_COLLECTION);
  }

  @Test
  public void shouldCollectNestedOutputElements() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                miBuilder.andThen(
                    m -> m.zeebeOutputElementExpression(OUTPUT_ELEMENT_EXPRESSION + ".nested"))))
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    // complete jobs
    completeJobs(
        processInstanceKey,
        INPUT_COLLECTION.size(),
        i -> Map.of("nested", OUTPUT_COLLECTION.get(i)));

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withName(OUTPUT_ELEMENT_EXPRESSION) // without '.nested'
                .withValue("null")
                .limit(INPUT_COLLECTION.size()))
        .hasSize(INPUT_COLLECTION.size());

    assertThat(
            RecordingExporter.variableRecords()
                .withName(OUTPUT_COLLECTION_VARIABLE)
                .withScopeKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasValue(JsonUtil.toJson(OUTPUT_COLLECTION));
  }

  @Test
  public void shouldCollectOutputElementsFromExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                miBuilder.andThen(
                    m ->
                        m.zeebeOutputElementExpression(
                            "number(string(loopCounter) + string(loopCounter))"))))
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    // complete jobs
    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .map(Record::getValue)
                .map(VariableRecordValue::getName))
        .noneMatch("number(string(loopCounter) + string(loopCounter))"::equals);

    assertThat(
            RecordingExporter.variableRecords()
                .withName(OUTPUT_COLLECTION_VARIABLE)
                .withScopeKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasValue(JsonUtil.toJson(OUTPUT_COLLECTION));
  }

  @Test
  public void shouldSetOutputCollectionVariable() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    final var variableRecord =
        RecordingExporter.variableRecords()
            .withName(OUTPUT_COLLECTION_VARIABLE)
            .withScopeKey(processInstanceKey)
            .getFirst();

    assertThat(variableRecord.getValue()).hasValue(JsonUtil.toJson(OUTPUT_COLLECTION));
  }

  @Test
  public void shouldCollectOutputInVariable() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    final var multiInstanceBody =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withName(OUTPUT_COLLECTION_VARIABLE)
                .withScopeKey(multiInstanceBody.getKey())
                .limit(INPUT_COLLECTION.size() + 1))
        .extracting(r -> r.getValue().getValue())
        .contains("[null,null,null]", "[11,null,null]", "[11,22,null]", "[11,22,33]");
  }

  @Test
  public void shouldSetOutputElementVariable() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withName(OUTPUT_ELEMENT_EXPRESSION)
                .limit(INPUT_COLLECTION.size()))
        .extracting(r -> r.getValue().getValue())
        .containsOnly("null");

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withName(OUTPUT_ELEMENT_EXPRESSION)
                .limit(INPUT_COLLECTION.size()))
        .extracting(r -> r.getValue().getValue())
        .containsExactlyElementsOf(
            OUTPUT_COLLECTION.stream().map(JsonUtil::toJson).collect(Collectors.toList()));
  }

  @Test
  public void shouldSetEmptyOutputCollectionIfSkip() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, List.of())
            .create();

    // then
    final var variableRecord =
        RecordingExporter.variableRecords()
            .withName(OUTPUT_COLLECTION_VARIABLE)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(variableRecord.getValue()).hasValue("[]");
  }

  @Test
  public void shouldIgnoreOutputCollectionVariableIfNotDefined() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                miBuilder.andThen(
                    m -> m.zeebeOutputCollection(null).zeebeOutputElementExpression(null))))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(OUTPUT_COLLECTION_VARIABLE);
  }

  @Test
  public void shouldNotPropagateOutputElementVariable() {
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(OUTPUT_ELEMENT_EXPRESSION);
  }

  @Test
  public void shouldSetLoopCounterVariable() {
    // given
    ENGINE.deployment().withXmlResource(process(miBuilder)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    final var elementInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("loopCounter"))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getValue()))
        .containsExactly(
            tuple(elementInstanceKeys.get(0), "1"),
            tuple(elementInstanceKeys.get(1), "2"),
            tuple(elementInstanceKeys.get(2), "3"));
  }

  @Test
  public void shouldApplyInputMapping() {
    // given
    final ServiceTask task = process(miBuilder).getModelElementById(ELEMENT_ID);
    final var process =
        task.builder()
            .zeebeInputExpression(INPUT_ELEMENT_VARIABLE, "x")
            .zeebeInputExpression("loopCounter", "y")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    final var elementInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getName(), v.getValue()))
        .contains(
            tuple(elementInstanceKeys.get(0), "x", JsonUtil.toJson(INPUT_COLLECTION.get(0))),
            tuple(elementInstanceKeys.get(0), "y", "1"),
            tuple(elementInstanceKeys.get(1), "x", JsonUtil.toJson(INPUT_COLLECTION.get(1))),
            tuple(elementInstanceKeys.get(1), "y", "2"),
            tuple(elementInstanceKeys.get(2), "x", JsonUtil.toJson(INPUT_COLLECTION.get(2))),
            tuple(elementInstanceKeys.get(2), "y", "3"));
  }

  @Test
  public void shouldApplyOutputMapping() {
    // given
    final ServiceTask task = process(miBuilder).getModelElementById(ELEMENT_ID);
    final var process =
        task.builder()
            .zeebeOutputExpression(
                "loopCounter", OUTPUT_ELEMENT_EXPRESSION) // overrides the variable
            .zeebeOutputExpression("loopCounter", "global") // propagates to root scope
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .withScopeKey(processInstanceKey)
                .withName(OUTPUT_COLLECTION_VARIABLE)
                .getFirst()
                .getValue())
        .hasValue("[1,2,3]");

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("global"))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getValue()))
        .containsExactly(
            tuple(processInstanceKey, "1"),
            tuple(processInstanceKey, "2"),
            tuple(processInstanceKey, "3"));
  }

  @Test
  public void shouldTriggerInterruptingBoundaryEvent() {
    // given
    final ServiceTask task = process(miBuilder).getModelElementById(ELEMENT_ID);
    final var process =
        task.builder()
            .boundaryEvent("boundary-event", b -> b.cancelActivity(true).message(MESSAGE_BUILDER))
            .sequenceFlowId("to-canceled")
            .endEvent("canceled")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of(
                    INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION,
                    MESSAGE_CORRELATION_KEY_VARIABLE, MESSAGE_CORRELATION_KEY))
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size() - 1);

    // make sure message subcription is opened, before publishing
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(MESSAGE_CORRELATION_KEY)
        .withTimeToLive(0) // must be 0 because engine is re-used in tests
        .publish();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(5))
        .extracting(Record::getIntent)
        .containsExactly(
            MessageSubscriptionIntent.CREATE,
            MessageSubscriptionIntent.CREATED,
            MessageSubscriptionIntent.CORRELATING,
            MessageSubscriptionIntent.CORRELATE,
            MessageSubscriptionIntent.CORRELATED);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r ->
                tuple(
                    r.getValue().getElementId(), r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                ELEMENT_ID,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                ELEMENT_ID, BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                "to-canceled",
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("canceled", BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(OUTPUT_COLLECTION_VARIABLE);
  }

  @Test
  public void shouldTriggerNonInterruptingBoundaryEvent() {
    // given
    final ServiceTask task = process(miBuilder).getModelElementById(ELEMENT_ID);
    final var process =
        task.builder()
            .boundaryEvent("boundary-event", b -> b.cancelActivity(false).message(MESSAGE_BUILDER))
            .sequenceFlowId("to-notified")
            .endEvent("notified")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of(
                    INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION,
                    MESSAGE_CORRELATION_KEY_VARIABLE, MESSAGE_CORRELATION_KEY))
            .create();

    completeJobs(processInstanceKey, INPUT_COLLECTION.size() - 1);

    // make sure message subscription is opened, before publishing
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(MESSAGE_CORRELATION_KEY)
        .withTimeToLive(0) // must be 0 because engine is re-used in tests
        .publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getValue().getBpmnElementType() == BpmnElementType.END_EVENT))
        .extracting(
            r ->
                tuple(
                    r.getValue().getElementId(), r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(
                "to-notified",
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("notified", BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT));

    // and
    completeJobs(processInstanceKey, 1);

    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(7))
        .extracting(Record::getIntent)
        .containsExactly(
            MessageSubscriptionIntent.CREATE,
            MessageSubscriptionIntent.CREATED,
            MessageSubscriptionIntent.CORRELATING,
            MessageSubscriptionIntent.CORRELATE,
            MessageSubscriptionIntent.CORRELATED,
            MessageSubscriptionIntent.DELETE,
            MessageSubscriptionIntent.DELETED);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r ->
                tuple(
                    r.getValue().getElementId(), r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple("notified", BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                ELEMENT_ID, BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private void completeJobs(final long processInstanceKey, final int count) {
    final Function<Integer, Object> defaultResultProvider = OUTPUT_COLLECTION::get;
    completeJobs(processInstanceKey, count, defaultResultProvider);
  }

  private void completeJobs(
      final long processInstanceKey,
      final int count,
      final Function<Integer, Object> resultProvider) {
    IntStream.range(0, count)
        .forEach(
            i -> {
              assertThat(
                      RecordingExporter.jobRecords(JobIntent.CREATED)
                          .withProcessInstanceKey(processInstanceKey)
                          .skip(i)
                          .exists())
                  .describedAs("Expected job %d/%d to be created", (i + 1), count)
                  .isTrue();

              final var jobBatch =
                  ENGINE.jobs().withType(jobType).withMaxJobsToActivate(1).activate().getValue();

              jobBatch
                  .getJobKeys()
                  .forEach(
                      jobKey ->
                          ENGINE
                              .job()
                              .withKey(jobKey)
                              .withVariable(OUTPUT_ELEMENT_EXPRESSION, resultProvider.apply(i))
                              .complete());
            });
  }
}

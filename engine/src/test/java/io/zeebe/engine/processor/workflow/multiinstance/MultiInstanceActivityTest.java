/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.multiinstance;

import static io.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.MultiInstanceLoopCharacteristicsBuilder;
import io.zeebe.model.bpmn.builder.zeebe.MessageBuilder;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
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

  private BpmnModelInstance workflow(
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
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  private static List<Tuple> sequentialLifecycle() {
    return Arrays.asList(
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldActivateActivitiesWithLoopCharacteristics() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(expectedLifecycle);
  }

  @Test
  public void shouldActivateActivitiesForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateOneJobForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(INPUT_COLLECTION.size()))
        .hasSize(INPUT_COLLECTION.size())
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getElementId)
        .containsOnly(ELEMENT_ID);
  }

  @Test
  public void shouldCompleteBodyWhenAllJobsAreCompleted() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldGoThroughMultiInstanceActivity() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldSetInputElementVariable() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

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
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withName(INPUT_ELEMENT_VARIABLE)
                .limit(3))
        .extracting(r -> r.getValue().getValue())
        .containsExactlyElementsOf(jsonInputCollection);
  }

  @Test
  public void shouldNotPropagateInputElementVariable() {
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withScopeKey(workflowInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(INPUT_ELEMENT_VARIABLE);
  }

  @Test
  public void shouldCancelJobsOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, 1);

    // when
    final Record<JobRecordValue> createdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skip(1)
            .getFirst();

    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(1))
        .hasSize(1)
        .extracting(Record::getKey)
        .containsExactly(createdJob.getKey());
  }

  @Test
  public void shouldTerminateInstancesOnTerminatingBody() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    final int completedJobs = INPUT_COLLECTION.size() - 1;
    completeJobs(workflowInstanceKey, completedJobs);

    // when
    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId(ELEMENT_ID)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .skip(completedJobs)
        .exists();

    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldSkipIfCollectionIsEmpty() {
    // when
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, Collections.emptyList())
            .create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .limit(4))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldIgnoreInputElementVariableIfNotDefined() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            workflow(
                miBuilder.andThen(
                    m ->
                        m.zeebeInputCollectionExpression(INPUT_COLLECTION_EXPRESSION)
                            .zeebeInputElement(null))))
        .deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

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
            workflow(
                miBuilder.andThen(
                    m ->
                        m.zeebeInputCollectionExpression("nested." + INPUT_COLLECTION_EXPRESSION))))
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(
                "nested", Collections.singletonMap(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(INPUT_COLLECTION.size())
        .exists();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

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
            workflow(
                miBuilder.andThen(
                    m -> m.zeebeOutputElementExpression(OUTPUT_ELEMENT_EXPRESSION + ".nested"))))
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    // complete jobs
    completeJobs(
        workflowInstanceKey,
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
                .withScopeKey(workflowInstanceKey)
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
            workflow(
                miBuilder.andThen(
                    m ->
                        m.zeebeOutputElementExpression(
                            "number(string(loopCounter) + string(loopCounter))"))))
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    // complete jobs
    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .map(Record::getValue)
                .map(VariableRecordValue::getName))
        .noneMatch("number(string(loopCounter) + string(loopCounter))"::equals);

    assertThat(
            RecordingExporter.variableRecords()
                .withName(OUTPUT_COLLECTION_VARIABLE)
                .withScopeKey(workflowInstanceKey)
                .getFirst()
                .getValue())
        .hasValue(JsonUtil.toJson(OUTPUT_COLLECTION));
  }

  @Test
  public void shouldSetOutputCollectionVariable() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final var workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    final var variableRecord =
        RecordingExporter.variableRecords()
            .withName(OUTPUT_COLLECTION_VARIABLE)
            .withScopeKey(workflowInstanceKey)
            .getFirst();

    assertThat(variableRecord.getValue()).hasValue(JsonUtil.toJson(OUTPUT_COLLECTION));
  }

  @Test
  public void shouldCollectOutputInVariable() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final var workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    final var multiInstanceBody =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
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
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final var workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

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
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, List.of())
            .create();

    // then
    final var variableRecord =
        RecordingExporter.variableRecords()
            .withName(OUTPUT_COLLECTION_VARIABLE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(variableRecord.getValue()).hasValue("[]");
  }

  @Test
  public void shouldIgnoreOutputCollectionVariableIfNotDefined() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            workflow(
                miBuilder.andThen(
                    m -> m.zeebeOutputCollection(null).zeebeOutputElementExpression(null))))
        .deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withScopeKey(workflowInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(OUTPUT_COLLECTION_VARIABLE);
  }

  @Test
  public void shouldNotPropagateOutputElementVariable() {
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withScopeKey(workflowInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(OUTPUT_ELEMENT_EXPRESSION);
  }

  @Test
  public void shouldSetLoopCounterVariable() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    final var elementInstanceKeys =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
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
    final ServiceTask task = workflow(miBuilder).getModelElementById(ELEMENT_ID);
    final var workflow =
        task.builder()
            .zeebeInputExpression(INPUT_ELEMENT_VARIABLE, "x")
            .zeebeInputExpression("loopCounter", "y")
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    final var elementInstanceKeys =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey))
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
    final ServiceTask task = workflow(miBuilder).getModelElementById(ELEMENT_ID);
    final var workflow =
        task.builder()
            .zeebeOutputExpression(
                "loopCounter", OUTPUT_ELEMENT_EXPRESSION) // overrides the variable
            .zeebeOutputExpression("loopCounter", "global") // propagates to root scope
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .withScopeKey(workflowInstanceKey)
                .withName(OUTPUT_COLLECTION_VARIABLE)
                .getFirst()
                .getValue())
        .hasValue("[1,2,3]");

    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withName("global"))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getScopeKey(), v.getValue()))
        .containsExactly(
            tuple(workflowInstanceKey, "1"),
            tuple(workflowInstanceKey, "2"),
            tuple(workflowInstanceKey, "3"));
  }

  @Test
  public void shouldTriggerInterruptingBoundaryEvent() {
    // given
    final ServiceTask task = workflow(miBuilder).getModelElementById(ELEMENT_ID);
    final var workflow =
        task.builder()
            .boundaryEvent("boundary-event", b -> b.cancelActivity(true).message(MESSAGE_BUILDER))
            .sequenceFlowId("to-canceled")
            .endEvent("canceled")
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of(
                    INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION,
                    MESSAGE_CORRELATION_KEY_VARIABLE, MESSAGE_CORRELATION_KEY))
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size() - 1);

    // when
    ENGINE
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(MESSAGE_CORRELATION_KEY)
        .withTimeToLive(0)
        .publish();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(4))
        .extracting(Record::getIntent)
        .containsExactly(
            MessageSubscriptionIntent.OPEN,
            MessageSubscriptionIntent.OPENED,
            MessageSubscriptionIntent.CORRELATE,
            MessageSubscriptionIntent.CORRELATED);

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(
            r ->
                tuple(
                    r.getValue().getElementId(), r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                ELEMENT_ID,
                BpmnElementType.SERVICE_TASK,
                WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                ELEMENT_ID,
                BpmnElementType.SERVICE_TASK,
                WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                "to-canceled",
                BpmnElementType.SEQUENCE_FLOW,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("canceled", BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .variableRecords()
                .withScopeKey(workflowInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain(OUTPUT_COLLECTION_VARIABLE);
  }

  @Test
  public void shouldTriggerNonInterruptingBoundaryEvent() {
    // given
    final ServiceTask task = workflow(miBuilder).getModelElementById(ELEMENT_ID);
    final var workflow =
        task.builder()
            .boundaryEvent("boundary-event", b -> b.cancelActivity(false).message(MESSAGE_BUILDER))
            .sequenceFlowId("to-notified")
            .endEvent("notified")
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of(
                    INPUT_COLLECTION_EXPRESSION, INPUT_COLLECTION,
                    MESSAGE_CORRELATION_KEY_VARIABLE, MESSAGE_CORRELATION_KEY))
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size() - 1);

    // when
    ENGINE
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(MESSAGE_CORRELATION_KEY)
        .withTimeToLive(0)
        .publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(r -> r.getValue().getBpmnElementType() == BpmnElementType.END_EVENT))
        .extracting(
            r ->
                tuple(
                    r.getValue().getElementId(), r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(
                "to-notified",
                BpmnElementType.SEQUENCE_FLOW,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                "notified", BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING));

    // and
    completeJobs(workflowInstanceKey, 1);

    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(6))
        .extracting(Record::getIntent)
        .containsExactly(
            MessageSubscriptionIntent.OPEN,
            MessageSubscriptionIntent.OPENED,
            MessageSubscriptionIntent.CORRELATE,
            MessageSubscriptionIntent.CORRELATED,
            MessageSubscriptionIntent.CLOSE,
            MessageSubscriptionIntent.CLOSED);

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(
            r ->
                tuple(
                    r.getValue().getElementId(), r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple("notified", BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                ELEMENT_ID, BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                ELEMENT_ID,
                BpmnElementType.MULTI_INSTANCE_BODY,
                WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  private void completeJobs(final long workflowInstanceKey, final int count) {
    final Function<Integer, Object> defaultResultProvider = OUTPUT_COLLECTION::get;
    completeJobs(workflowInstanceKey, count, defaultResultProvider);
  }

  private void completeJobs(
      final long workflowInstanceKey,
      final int count,
      final Function<Integer, Object> resultProvider) {
    IntStream.range(0, count)
        .forEach(
            i -> {
              assertThat(
                      RecordingExporter.jobRecords(JobIntent.CREATED)
                          .withWorkflowInstanceKey(workflowInstanceKey)
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

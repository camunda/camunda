/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class TaskListenerTestHelper {

  private static final String PROCESS_ID = "process";
  private static final String USER_TASK_ELEMENT_ID = "my_user_task";

  private final EngineRule engine;

  public TaskListenerTestHelper(final EngineRule engine) {
    this.engine = engine;
  }

  void completeRecreatedJobWithType(final long processInstanceKey, final String jobType) {
    final long jobKey = findRecreatedJobKey(processInstanceKey, jobType);
    engine.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }

  void completeRecreatedJobWithTypeAndResult(
      final long processInstanceKey, final String jobType, final JobResult jobResult) {
    final long jobKey = findRecreatedJobKey(processInstanceKey, jobType);
    engine.job().ofInstance(processInstanceKey).withKey(jobKey).withResult(jobResult).complete();
  }

  long findRecreatedJobKey(final long processInstanceKey, final String jobType) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .skip(1)
        .getFirst()
        .getKey();
  }

  void assertThatProcessInstanceCompleted(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            io.camunda.zeebe.protocol.record.Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  BpmnModelInstance createProcessWithZeebeUserTask(
      final UnaryOperator<UserTaskBuilder> userTaskBuilderFunction) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask(USER_TASK_ELEMENT_ID, t -> userTaskBuilderFunction.apply(t.zeebeUserTask()))
        .endEvent()
        .done();
  }

  BpmnModelInstance createProcess(
      final Function<StartEventBuilder, AbstractFlowNodeBuilder<?, ?>> processBuilderFunction) {
    return processBuilderFunction
        .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
        .endEvent()
        .done();
  }

  BpmnModelInstance createProcessWithAssigningTaskListeners(final String... listenerTypes) {
    return createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.assigning, listenerTypes);
  }

  BpmnModelInstance createUserTaskWithTaskListenersAndAssignee(
      final String listenerType, final String assignee) {
    return createProcessWithZeebeUserTask(
        taskBuilder ->
            taskBuilder
                .zeebeAssignee(assignee)
                .zeebeTaskListener(l -> l.assigning().type(listenerType)));
  }

  BpmnModelInstance createProcessWithCompletingTaskListeners(final String... listenerTypes) {
    return createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.completing, listenerTypes);
  }

  BpmnModelInstance createUserTaskWithTaskListeners(
      final ZeebeTaskListenerEventType listenerType, final String... listenerTypes) {
    return createProcessWithZeebeUserTask(
        taskBuilder -> {
          Stream.of(listenerTypes)
              .forEach(
                  type -> taskBuilder.zeebeTaskListener(l -> l.eventType(listenerType).type(type)));
          return taskBuilder;
        });
  }

  long createProcessInstance(final BpmnModelInstance modelInstance) {
    return createProcessInstanceWithVariables(modelInstance, Collections.emptyMap());
  }

  long createProcessInstanceWithVariables(
      final BpmnModelInstance modelInstance, final Map<String, Object> processVariables) {
    engine.deployment().withXmlResource(modelInstance).deploy();
    return engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariables(processVariables)
        .create();
  }

  void completeJobs(final long processInstanceKey, final String... jobTypes) {
    for (final String jobType : jobTypes) {
      engine.job().ofInstance(processInstanceKey).withType(jobType).complete();
    }
  }

  void completeRecreatedJobs(final long processInstanceKey, final String... jobTypes) {
    for (final String jobType : jobTypes) {
      completeRecreatedJobWithType(processInstanceKey, jobType);
    }
  }

  JobRecordValue activateJob(final long processInstanceKey, final String jobType) {
    return engine.jobs().withType(jobType).activate().getValue().getJobs().stream()
        .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No job found with type " + jobType));
  }

  void assertActivatedJob(
      final long processInstanceKey,
      final String jobType,
      final Consumer<JobRecordValue> assertion) {
    final var activatedJob = activateJob(processInstanceKey, jobType);
    assertThat(activatedJob).satisfies(assertion);
  }

  FormMetadataValue deployForm(final String formPath) {
    final var deploymentEvent = engine.deployment().withJsonClasspathResource(formPath).deploy();

    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    final var formMetadata = deploymentEvent.getValue().getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    return formMetadata.getFirst();
  }

  void assertTaskListenerJobsCompletionSequence(
      final long processInstanceKey,
      final JobListenerEventType eventType,
      final String... listenerTypes) {
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(eventType)
                .withIntent(JobIntent.COMPLETED)
                .limit(listenerTypes.length))
        .extracting(io.camunda.zeebe.protocol.record.Record::getValue)
        .extracting(JobRecordValue::getType)
        .describedAs("Verify that all task listeners were completed in the correct sequence")
        .containsExactly(listenerTypes);
  }

  void assertUserTaskIntentsSequence(
      final long processInstanceKey, final UserTaskIntent... intents) {
    assertThat(intents).describedAs("Expected intents not to be empty").isNotEmpty();
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == intents[intents.length - 1]))
        .extracting(io.camunda.zeebe.protocol.record.Record::getIntent)
        .describedAs("Verify the expected sequence of User Task intents")
        .containsSequence(intents);
  }

  JobListenerEventType mapToJobListenerEventType(final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case ZeebeTaskListenerEventType.assigning -> JobListenerEventType.ASSIGNING;
      case ZeebeTaskListenerEventType.updating -> JobListenerEventType.UPDATING;
      case ZeebeTaskListenerEventType.completing -> JobListenerEventType.COMPLETING;
      default ->
          throw new IllegalArgumentException(
              "Unsupported zeebe task listener event type: '%s'".formatted(eventType));
    };
  }

  void assertUserTaskRecordWithIntent(
      final long processInstanceKey,
      final UserTaskIntent intent,
      final Consumer<UserTaskRecordValue> consumer) {
    assertThat(
            RecordingExporter.userTaskRecords(intent)
                .withProcessInstanceKey(processInstanceKey)
                .findFirst()
                .map(Record::getValue))
        .describedAs("Expected to have User Task record with '%s' intent", intent)
        .hasValueSatisfying(consumer);
  }
}

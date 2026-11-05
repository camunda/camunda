/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ExecutionListenerJobTest {
  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true);

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void createClient() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldCompleteFirstExecutionListenerJobWithoutVariables() {
    // given
    final String startElJobType = uniqueType("start-el");
    final String serviceTaskJobType = uniqueType("job");

    // create model with 1 start EL for service task
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType(serviceTaskJobType).zeebeStartExecutionListener(startElJobType))
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(modelInstance);
    resourcesHelper.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    client.newWorker().jobType(startElJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());

    final long elJobKey = getHandledJobKey(elJobHandler, startElJobType);

    // when
    client.newCompleteCommand(elJobKey).send().join();

    // then: start EL job completed
    ZeebeAssertHelper.assertJobCompleted(
        startElJobType, (job) -> assertThat(job.getVariables()).isEmpty());

    // service task job created
    ZeebeAssertHelper.assertJobCreated(
        serviceTaskJobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @Test
  void shouldCompleteFirstExecutionListenerJobWithVariablesAndNextElShouldAccessThem() {
    // given
    final String firstStartElJobType = uniqueType("start-el").concat("-1");
    final String secondStartElJobType = uniqueType("start-el").concat("-2");
    final String serviceTaskJobType = uniqueType("job");

    // create model with 2 start ELs
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(serviceTaskJobType)
                        .zeebeStartExecutionListener(firstStartElJobType)
                        .zeebeStartExecutionListener(secondStartElJobType))
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(modelInstance);
    resourcesHelper.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    client.newWorker().jobType(firstStartElJobType).handler(elJobHandler).open();
    client.newWorker().jobType(secondStartElJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());
    final long firstElJobKey = getHandledJobKey(elJobHandler, firstStartElJobType);

    // when
    client.newCompleteCommand(firstElJobKey).variable("el1_var_a", "value_a").send().join();

    waitUntil(() -> elJobHandler.getHandledJobs().size() == 2);

    // then: start EL job completed
    // Variables in job complete commands are wiped to avoid exceeded record batch size exceptions
    // https://github.com/camunda/camunda/issues/14006
    ZeebeAssertHelper.assertJobCompleted(
        firstStartElJobType, (job) -> assertThat(job.getVariables()).isEmpty());

    // service task job created
    ZeebeAssertHelper.assertJobCreated(secondStartElJobType);
    final ActivatedJob secondElActivatedJob = elJobHandler.getHandledJobs().getLast();
    assertThat(secondElActivatedJob)
        .satisfies(
            job -> {
              assertThat(job.getType()).isEqualTo(secondStartElJobType);
              assertThat(job.getVariablesAsMap()).containsOnly(entry("el1_var_a", "value_a"));
            });
  }

  @Test
  void shouldCompleteProcessWithExecutionListeners() {
    // given
    final String processStartElJobType = uniqueType("start-el");
    final String processEndElJobType = uniqueType("end-el");
    final String serviceTaskJobType = uniqueType("job");

    // create process model with `start` and `end` ELs
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .zeebeStartExecutionListener(processStartElJobType)
            .zeebeEndExecutionListener(processEndElJobType)
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType(serviceTaskJobType))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(modelInstance);
    final long processInstanceKey =
        resourcesHelper.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    client.newWorker().jobType(processStartElJobType).handler(elJobHandler).open();
    client.newWorker().jobType(serviceTaskJobType).handler(elJobHandler).open();
    client.newWorker().jobType(processEndElJobType).handler(elJobHandler).open();

    // when
    // complete process start EL job
    completeJobWithType(elJobHandler, processStartElJobType);

    // complete service task job
    completeJobWithType(elJobHandler, serviceTaskJobType);

    // complete process end EL job
    completeJobWithType(elJobHandler, processEndElJobType);

    // assert that the process was completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  void shouldCompleteBeforeAllExecutionListenerJobOnMultiInstanceActivity() {
    // given - multi-instance service task with a single beforeAll execution listener
    final String beforeAllElJobType = uniqueType("before-all-el");
    final String serviceTaskJobType = uniqueType("job");
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(serviceTaskJobType)
                        .zeebeBeforeAllExecutionListener(beforeAllElJobType)
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(modelInstance);
    final long processInstanceKey =
        resourcesHelper.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    final RecordingJobHandler serviceTaskHandler = new RecordingJobHandler();
    client.newWorker().jobType(beforeAllElJobType).handler(elJobHandler).open();
    client.newWorker().jobType(serviceTaskJobType).handler(serviceTaskHandler).open();

    // when
    waitUntilJobHandled(elJobHandler, beforeAllElJobType);
    final ActivatedJob beforeAllJob = elJobHandler.getHandledJobs().getFirst();

    // then: beforeAll job exists and the multi-instance body has not yet activated any inner
    // service task jobs
    assertThat(beforeAllJob.getType()).isEqualTo(beforeAllElJobType);
    assertThat(serviceTaskHandler.getHandledJobs())
        .as("service task jobs should not be created before the beforeAll listener completes")
        .isEmpty();

    final List<Integer> items = List.of(1, 2, 3);
    client
        .newCompleteCommand(beforeAllJob.getKey())
        .variables(Map.of("items", items))
        .send()
        .join();

    // and: each inner instance produces a service task job, and the process completes
    waitUntil(() -> serviceTaskHandler.getHandledJobs().size() == items.size());
    serviceTaskHandler
        .getHandledJobs()
        .forEach(j -> client.newCompleteCommand(j.getKey()).send().join());

    waitUntil(
        () ->
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists());

    // and: the multi-instance body activated only after the beforeAll listener completed
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.MULTI_INSTANCE_BODY,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  void shouldCompleteBeforeAllJobVariablesAreVisibleToInnerInstanceWorkers() {
    // given - multi-instance service task with a beforeAll execution listener
    final String beforeAllElJobType = uniqueType("before-all-el");
    final String serviceTaskJobType = uniqueType("job");
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(serviceTaskJobType)
                        .zeebeBeforeAllExecutionListener(beforeAllElJobType)
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(modelInstance);
    resourcesHelper.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    final RecordingJobHandler serviceTaskHandler = new RecordingJobHandler();
    client.newWorker().jobType(beforeAllElJobType).handler(elJobHandler).open();
    client.newWorker().jobType(serviceTaskJobType).handler(serviceTaskHandler).open();

    waitUntilJobHandled(elJobHandler, beforeAllElJobType);
    final ActivatedJob beforeAllJob = elJobHandler.getHandledJobs().getFirst();

    // when: complete the beforeAll listener with the loop collection AND a non-collection variable
    final List<Integer> items = List.of(1, 2, 3);
    final String varName = "foo";
    final String varValue = "bar";
    client
        .newCompleteCommand(beforeAllJob.getKey())
        .variables(Map.of("items", items, varName, varValue))
        .send()
        .join();

    // then: every inner instance worker sees the non-collection variable set by the beforeAll
    // listener
    waitUntil(() -> serviceTaskHandler.getHandledJobs().size() == items.size());
    assertThat(serviceTaskHandler.getHandledJobs())
        .hasSize(items.size())
        .allSatisfy(job -> assertThat(job.getVariablesAsMap()).containsEntry(varName, varValue));
  }

  private void completeJobWithType(
      final RecordingJobHandler elJobHandler, final String processStartElJobType) {
    waitUntilJobHandled(elJobHandler, processStartElJobType);
    final long processStartElJobKey = getHandledJobKey(elJobHandler, processStartElJobType);

    client.newCompleteCommand(processStartElJobKey).send().join();
  }

  private static String uniqueType(final String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  private static void waitUntilJobHandled(
      final RecordingJobHandler elJobHandler, final String jobType) {
    waitUntil(
        () -> elJobHandler.getHandledJobs().stream().anyMatch(j -> j.getType().equals(jobType)));
  }

  private static long getHandledJobKey(final RecordingJobHandler jobHandler, final String jobType) {
    return jobHandler.getHandledJobs().stream()
        .filter(j -> j.getType().equals(jobType))
        .toList()
        .getFirst()
        .getKey();
  }
}

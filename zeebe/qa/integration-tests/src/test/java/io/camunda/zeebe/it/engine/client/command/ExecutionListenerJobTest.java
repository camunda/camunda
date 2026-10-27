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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExecutionListenerJobTest {
  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCompleteFirstExecutionListenerJobWithoutVariables() {
    // given
    final String startElJobType = helper.getStartExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();

    // create model with 1 start EL for service task
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType(serviceTaskJobType).zeebeStartExecutionListener(startElJobType))
            .done();
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(startElJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());

    final long elJobKey = getHandledJobKey(elJobHandler, startElJobType);

    // when
    CLIENT_RULE.getClient().newCompleteCommand(elJobKey).send().join();

    // then: start EL job completed
    ZeebeAssertHelper.assertJobCompleted(
        startElJobType, (job) -> assertThat(job.getVariables()).isEmpty());

    // service task job created
    ZeebeAssertHelper.assertJobCreated(
        serviceTaskJobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @Test
  public void shouldCompleteFirstExecutionListenerJobWithVariablesAndNextElShouldAccessThem() {
    // given
    final String firstStartElJobType = helper.getStartExecutionListenerType().concat("-1");
    final String secondStartElJobType = helper.getStartExecutionListenerType().concat("-2");
    final String serviceTaskJobType = helper.getJobType();

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
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(firstStartElJobType).handler(elJobHandler).open();
    CLIENT_RULE.getClient().newWorker().jobType(secondStartElJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());
    final long firstElJobKey = getHandledJobKey(elJobHandler, firstStartElJobType);

    // when
    CLIENT_RULE
        .getClient()
        .newCompleteCommand(firstElJobKey)
        .variable("el1_var_a", "value_a")
        .send()
        .join();

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
  public void shouldCompleteProcessWithExecutionListeners() {
    // given
    final String processStartElJobType = helper.getStartExecutionListenerType();
    final String processEndElJobType = helper.getEndExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();

    // create process model with `start` and `end` ELs
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .zeebeStartExecutionListener(processStartElJobType)
            .zeebeEndExecutionListener(processEndElJobType)
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType(serviceTaskJobType))
            .endEvent("end")
            .done();
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(processStartElJobType).handler(elJobHandler).open();
    CLIENT_RULE.getClient().newWorker().jobType(serviceTaskJobType).handler(elJobHandler).open();
    CLIENT_RULE.getClient().newWorker().jobType(processEndElJobType).handler(elJobHandler).open();

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
  public void shouldCompleteBeforeAllExecutionListenerJobOnMultiInstanceActivity() {
    // given - multi-instance service task with a single beforeAll execution listener
    final String beforeAllElJobType = helper.getBeforeAllExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();
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
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    final RecordingJobHandler serviceTaskHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(beforeAllElJobType).handler(elJobHandler).open();
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(serviceTaskJobType)
        .handler(serviceTaskHandler)
        .open();

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
    CLIENT_RULE
        .getClient()
        .newCompleteCommand(beforeAllJob.getKey())
        .variables(Map.of("items", items))
        .send()
        .join();

    // and: each inner instance produces a service task job, and the process completes
    waitUntil(() -> serviceTaskHandler.getHandledJobs().size() == items.size());
    serviceTaskHandler
        .getHandledJobs()
        .forEach(j -> CLIENT_RULE.getClient().newCompleteCommand(j.getKey()).send().join());

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
  public void shouldCompleteBeforeAllJobVariablesAreVisibleToInnerInstanceWorkers() {
    // given - multi-instance service task with a beforeAll execution listener
    final String beforeAllElJobType = helper.getBeforeAllExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();
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
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    final RecordingJobHandler serviceTaskHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(beforeAllElJobType).handler(elJobHandler).open();
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(serviceTaskJobType)
        .handler(serviceTaskHandler)
        .open();

    waitUntilJobHandled(elJobHandler, beforeAllElJobType);
    final ActivatedJob beforeAllJob = elJobHandler.getHandledJobs().getFirst();

    // when: complete the beforeAll listener with the loop collection AND a non-collection variable
    final List<Integer> items = List.of(1, 2, 3);
    final String varName = "foo";
    final String varValue = "bar";
    CLIENT_RULE
        .getClient()
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

  private static void completeJobWithType(
      final RecordingJobHandler elJobHandler, final String processStartElJobType) {
    waitUntilJobHandled(elJobHandler, processStartElJobType);
    final long processStartElJobKey = getHandledJobKey(elJobHandler, processStartElJobType);

    CLIENT_RULE.getClient().newCompleteCommand(processStartElJobKey).send().join();
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

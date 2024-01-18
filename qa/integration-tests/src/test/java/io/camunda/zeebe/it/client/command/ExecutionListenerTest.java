/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExecutionListenerTest {
  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCompleteFirstExecutionListenerJobWithoutVariables() {
    // given
    final String elJobType = helper.getStartExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();

    // create model with one EL[start]
    final BpmnModelInstance modelInstance =
        CLIENT_RULE.createSingleJobModelInstance(
            serviceTaskJobType,
            b ->
                b.zeebeStartExecutionListener(elJobType)
                    .zeebeEndExecutionListener(helper.getEndExecutionListenerType()));
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(elJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());

    final ActivatedJob elJobEvent = elJobHandler.getHandledJobs().getFirst();
    final long elJobKey = elJobEvent.getKey();

    // when
    CLIENT_RULE.getClient().newCompleteCommand(elJobKey).send().join();

    // then
    // EL[start] job completed
    ZeebeAssertHelper.assertJobCompleted(
        elJobType, (job) -> assertThat(job.getVariables()).isEmpty());

    // service task job created
    ZeebeAssertHelper.assertJobCreated(
        serviceTaskJobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @Test
  public void shouldCompleteFirstExecutionListenerJobWithVariablesAndNextElShouldAccessThem() {
    // given
    final String firstElJobType = helper.getStartExecutionListenerType();
    final String secondElJobType = helper.getStartExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();

    // create model with 2 EL[start]
    final BpmnModelInstance modelInstance =
        CLIENT_RULE.createSingleJobModelInstance(
            serviceTaskJobType,
            b ->
                b.zeebeStartExecutionListener(firstElJobType)
                    .zeebeStartExecutionListener(secondElJobType));
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(firstElJobType).handler(elJobHandler).open();
    CLIENT_RULE.getClient().newWorker().jobType(secondElJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());

    final long firstElJobKey = elJobHandler.getHandledJobs().getFirst().getKey();

    // when
    CLIENT_RULE
        .getClient()
        .newCompleteCommand(firstElJobKey)
        .variable("el1_var_a", "value_a")
        .send()
        .join();

    waitUntil(() -> elJobHandler.getHandledJobs().size() == 2);

    // then
    // EL[start] job completed
    ZeebeAssertHelper.assertJobCompleted(
        firstElJobType,
        (job) -> assertThat(job.getVariables()).containsOnly(entry("el1_var_a", "value_a")));

    // service task job created
    final ActivatedJob secondElActivatedJob = elJobHandler.getHandledJobs().getLast();
    assertThat(secondElActivatedJob.getType()).isEqualTo(secondElJobType);
    assertThat(secondElActivatedJob.getVariablesAsMap())
        .containsOnly(entry("el1_var_a", "value_a"));
    ZeebeAssertHelper.assertJobCreated(secondElJobType);
  }

  @Test
  public void shouldCompleteProcessExecutionListeners() {
    // given
    final String processStartElJobType = helper.getStartExecutionListenerType();
    final String processEndElJobType = helper.getEndExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();

    // create model with one EL[start]
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
    // process EL[start] job activated
    waitUntilJobHandled(elJobHandler, processStartElJobType);
    final long processStartElJobKey = getHandledJobKey(elJobHandler, processStartElJobType);

    // then
    // complete process EL[start] job activated
    CLIENT_RULE.getClient().newCompleteCommand(processStartElJobKey).send().join();

    // when
    // service task job activated
    waitUntilJobHandled(elJobHandler, serviceTaskJobType);
    final long serviceTaskJobKey = getHandledJobKey(elJobHandler, serviceTaskJobType);

    // then
    // complete service task job
    CLIENT_RULE.getClient().newCompleteCommand(serviceTaskJobKey).send().join();

    // when
    // process EL[end] job activated
    waitUntilJobHandled(elJobHandler, processEndElJobType);
    final long processEndElJobKey = getHandledJobKey(elJobHandler, processEndElJobType);

    // then
    // complete EL[end] job activated
    CLIENT_RULE.getClient().newCompleteCommand(processEndElJobKey).send().join();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteStartEventExecutionListeners() {
    // given
    final String startEventStartElJobType = helper.getStartExecutionListenerType();
    final String startEventEndElJobType = helper.getEndExecutionListenerType();
    final String serviceTaskJobType = helper.getJobType();

    // create model with one EL[start] for start and end event
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .zeebeStartExecutionListener(startEventStartElJobType)
            .zeebeEndExecutionListener(startEventEndElJobType)
            .serviceTask("task", t -> t.zeebeJobType(serviceTaskJobType))
            .endEvent("end")
            .done();
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(startEventStartElJobType)
        .handler(elJobHandler)
        .open();
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(startEventEndElJobType)
        .handler(elJobHandler)
        .open();
    CLIENT_RULE.getClient().newWorker().jobType(serviceTaskJobType).handler(elJobHandler).open();

    // when
    // start event EL[start] job activated
    waitUntilJobHandled(elJobHandler, startEventStartElJobType);
    final long startEventStartElJobKey = getHandledJobKey(elJobHandler, startEventStartElJobType);

    // then
    // complete start eventEL[start] job activated
    CLIENT_RULE.getClient().newCompleteCommand(startEventStartElJobKey).send().join();

    // when
    // start event EL[end] job activated
    waitUntilJobHandled(elJobHandler, startEventEndElJobType);
    final long startEventEndElJobKey = getHandledJobKey(elJobHandler, startEventEndElJobType);

    // then
    // complete start eventEL[end] job activated
    CLIENT_RULE.getClient().newCompleteCommand(startEventEndElJobKey).send().join();

    // when
    // service task job activated
    waitUntilJobHandled(elJobHandler, serviceTaskJobType);
    final long serviceTaskJobKey = getHandledJobKey(elJobHandler, serviceTaskJobType);

    // then
    // complete service task job
    CLIENT_RULE.getClient().newCompleteCommand(serviceTaskJobKey).send().join();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static long getHandledJobKey(
      final RecordingJobHandler elJobHandler, final String jobType) {
    return elJobHandler.getHandledJobs().stream()
        .filter(j -> j.getType().equals(jobType))
        .findFirst()
        .get()
        .getKey();
  }

  private static void waitUntilJobHandled(
      final RecordingJobHandler elJobHandler, final String jobType) {
    waitUntil(
        () -> elJobHandler.getHandledJobs().stream().anyMatch(j -> j.getType().equals(jobType)));
  }
}

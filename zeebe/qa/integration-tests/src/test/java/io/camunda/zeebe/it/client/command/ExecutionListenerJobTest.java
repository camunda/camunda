/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
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

    // create model with one EL[start]
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(serviceTaskJobType)
                        .zeebeStartExecutionListener(startElJobType)
                        .zeebeEndExecutionListener(helper.getEndExecutionListenerType()))
            .done();
    final long processDefinitionKey = CLIENT_RULE.deployProcess(modelInstance);
    CLIENT_RULE.createProcessInstance(processDefinitionKey, "{}");

    final RecordingJobHandler elJobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(startElJobType).handler(elJobHandler).open();

    waitUntil(() -> !elJobHandler.getHandledJobs().isEmpty());

    final ActivatedJob elJobEvent = elJobHandler.getHandledJobs().getFirst();
    final long elJobKey = elJobEvent.getKey();

    // when
    CLIENT_RULE.getClient().newCompleteCommand(elJobKey).send().join();

    // then: EL[start] job completed
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

    // create model with 2 EL[start]
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

    final long firstElJobKey = elJobHandler.getHandledJobs().getFirst().getKey();

    // when
    CLIENT_RULE
        .getClient()
        .newCompleteCommand(firstElJobKey)
        .variable("el1_var_a", "value_a")
        .send()
        .join();

    waitUntil(() -> elJobHandler.getHandledJobs().size() == 2);

    // then: EL[start] job completed
    ZeebeAssertHelper.assertJobCompleted(
        firstStartElJobType,
        (job) -> assertThat(job.getVariables()).containsOnly(entry("el1_var_a", "value_a")));

    // service task job created
    ZeebeAssertHelper.assertJobCreated(secondStartElJobType);
    final ActivatedJob secondElActivatedJob = elJobHandler.getHandledJobs().getLast();
    assertThat(secondElActivatedJob.getType()).isEqualTo(secondStartElJobType);
    assertThat(secondElActivatedJob.getVariablesAsMap())
        .containsOnly(entry("el1_var_a", "value_a"));
  }
}

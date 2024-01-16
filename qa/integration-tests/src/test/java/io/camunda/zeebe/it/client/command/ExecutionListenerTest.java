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

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
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

    // create model with 1 start EL[start]
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
    ZeebeAssertHelper.assertJobCreated(serviceTaskJobType);
  }
}

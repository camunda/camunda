/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.network;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.it.util.ZeebeAssertHelper;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.util.ByteValue;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class LargeMessageSizeTest {

  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofMegabytes(5);
  // only use half of the max message size because some commands produce two events
  private static final long LARGE_SIZE = ByteValue.ofMegabytes(1);
  private static final long METADATA_SIZE = 512;

  private static final String LARGE_TEXT = "x".repeat((int) (LARGE_SIZE - METADATA_SIZE));

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(b -> b.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE));
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;

  private static BpmnModelInstance workflow(final String jobType) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldDeployLargeWorkflow() {
    // given
    final var workflowAsString = Bpmn.convertToString(workflow(jobType));
    final var additionalChars = "<!--" + LARGE_TEXT + "-->";
    final var largeWorkflow = workflowAsString + additionalChars;

    // when
    final var deployment =
        CLIENT_RULE
            .getClient()
            .newDeployCommand()
            .addResourceStringUtf8(largeWorkflow, "process.bpmn")
            .send()
            .join();

    final var workflowKey = deployment.getWorkflows().get(0).getWorkflowKey();

    // then
    final var workflowInstanceEvent =
        CLIENT_RULE.getClient().newCreateInstanceCommand().workflowKey(workflowKey).send().join();

    ZeebeAssertHelper.assertWorkflowInstanceCreated(workflowInstanceEvent.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateInstanceWithLargeVariables() {
    // given
    final var workflowKey = CLIENT_RULE.deployWorkflow(workflow(jobType));

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    final var workflowInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .workflowKey(workflowKey)
            .variables(largeVariables)
            .send()
            .join();

    // then
    ZeebeAssertHelper.assertWorkflowInstanceCreated(workflowInstanceEvent.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCompleteJobWithLargeVariables() {
    // given
    final var workflowKey = CLIENT_RULE.deployWorkflow(workflow(jobType));

    final var workflowInstanceEvent =
        CLIENT_RULE.getClient().newCreateInstanceCommand().workflowKey(workflowKey).send().join();

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(jobType)
        .handler(
            ((client, job) ->
                client.newCompleteCommand(job.getKey()).variables(largeVariables).send().join()))
        .open();

    // then
    ZeebeAssertHelper.assertWorkflowInstanceCompleted(
        workflowInstanceEvent.getWorkflowInstanceKey());
  }
}

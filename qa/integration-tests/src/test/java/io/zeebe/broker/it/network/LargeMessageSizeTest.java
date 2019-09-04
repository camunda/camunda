/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.network;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.ZeebeAssertHelper;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.ByteValue;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class LargeMessageSizeTest {

  private static final ByteValue MAX_MESSAGE_SIZE = ByteValue.ofMegabytes(4);
  // only use half of the max message size because some commands produce two events
  private static final ByteValue LARGE_SIZE = ByteValue.ofMegabytes(2);
  private static final ByteValue METADATA_SIZE = ByteValue.ofBytes(512);

  private static final String LARGE_TEXT =
      "x".repeat((int) LARGE_SIZE.toBytes() - (int) METADATA_SIZE.toBytes());

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType(JOB_TYPE))
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(b -> b.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE.toString()));
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldDeployLargeWorkflow() {
    // given
    final var workflowAsString = Bpmn.convertToString(WORKFLOW);
    final var additionalChars = "<!--" + LARGE_TEXT + "-->";
    final var largeWorkflow = workflowAsString + additionalChars;

    // when
    clientRule
        .getClient()
        .newDeployCommand()
        .addResourceStringUtf8(largeWorkflow, "process.bpmn")
        .send()
        .join();

    // then
    final var workflowInstanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    ZeebeAssertHelper.assertWorkflowInstanceCreated(workflowInstanceEvent.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateInstanceWithLargeVariables() {
    // given
    clientRule.deployWorkflow(WORKFLOW);

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    final var workflowInstanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(largeVariables)
            .send()
            .join();

    // then
    ZeebeAssertHelper.assertWorkflowInstanceCreated(workflowInstanceEvent.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCompleteJobWithLargeVariables() {
    // given
    clientRule.deployWorkflow(WORKFLOW);

    final var workflowInstanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    clientRule
        .getClient()
        .newWorker()
        .jobType(JOB_TYPE)
        .handler(
            ((client, job) ->
                client.newCompleteCommand(job.getKey()).variables(largeVariables).send().join()))
        .open();

    // then
    ZeebeAssertHelper.assertWorkflowInstanceCompleted(
        workflowInstanceEvent.getWorkflowInstanceKey());
  }
}

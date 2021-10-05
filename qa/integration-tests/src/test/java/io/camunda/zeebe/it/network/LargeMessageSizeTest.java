/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.network;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.util.ByteValue;
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

  private static BpmnModelInstance process(final String jobType) {
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
  public void shouldDeployLargeProcess() {
    // given
    final var processAsString = Bpmn.convertToString(process(jobType));
    final var additionalChars = "<!--" + LARGE_TEXT + "-->";
    final var largeProcess = processAsString + additionalChars;

    // when
    final var deployment =
        CLIENT_RULE
            .getClient()
            .newDeployCommand()
            .addResourceStringUtf8(largeProcess, "process.bpmn")
            .send()
            .join();

    final var processDefinitionKey = deployment.getProcesses().get(0).getProcessDefinitionKey();

    // then
    final var processInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    ZeebeAssertHelper.assertProcessInstanceCreated(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  public void shouldCreateInstanceWithLargeVariables() {
    // given
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process(jobType));

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    final var processInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(largeVariables)
            .send()
            .join();

    // then
    ZeebeAssertHelper.assertProcessInstanceCreated(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  public void shouldCompleteJobWithLargeVariables() {
    // given
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process(jobType));

    final var processInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

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
    ZeebeAssertHelper.assertProcessInstanceCompleted(processInstanceEvent.getProcessInstanceKey());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.it.util.BrokerClassRuleHelper;
import io.camunda.zeebe.broker.it.util.GrpcClientRule;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public class SmallMessageSizeTest {
  private static final int VARIABLE_COUNT = 4;
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(12);
  private static final String LARGE_TEXT =
      "x".repeat((int) (MAX_MESSAGE_SIZE.toBytes() / VARIABLE_COUNT));
  private static final String JOB_TYPE = "acme";

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(
          b -> {
            b.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE);
            b.getGateway().getLongPolling().setEnabled(false);
          });
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long processInstanceKey;

  private static BpmnModelInstance process(final String jobType) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  @Before
  public void givenProcessWithLargeVariables() {
    // given a process
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process(JOB_TYPE));

    processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey);
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .exists();

    // with variables that are greater than the message size
    for (int i = 0; i < VARIABLE_COUNT; i++) {
      CLIENT_RULE
          .getClient()
          .newSetVariablesCommand(processInstanceKey)
          .variables(Map.of(String.format("large-%d", i), LARGE_TEXT))
          .send()
          .join();
    }
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(v -> v.getValue().getName().startsWith("large"))
                .limit(4))
        .hasSize(4);
  }

  @Test
  public void shouldSkipJobsThatExceedMessageSize() {
    // when (we activate jobs)
    final var response =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .send()
            .join(10, TimeUnit.SECONDS);

    // then the job of the process which is too big for the message is ignored
    assertThat(response.getJobs()).isEmpty();
  }

  @Test
  public void shouldActivateJobIfRequestVariablesFitIntoMessageSize() {
    // when (we activate job, but select only a subset of the variables - a subset that fits into
    // the message size)
    final var response =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .fetchVariables("0")
            .send()
            .join(10, TimeUnit.SECONDS);

    // then (the job is activated)
    assertThat(response.getJobs()).hasSize(1);
    assertThat(response.getJobs().get(0).getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }
}

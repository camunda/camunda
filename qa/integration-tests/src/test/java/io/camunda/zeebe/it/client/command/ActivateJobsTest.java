/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.camunda.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.Strings;
import java.util.Map;
import org.agrona.BitUtil;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ActivateJobsTest {

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(ActivateJobsTest::disableLongPolling);
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;

  private static void disableLongPolling(final BrokerCfg config) {
    config.getGateway().getLongPolling().setEnabled(false);
  }

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  @Test(timeout = 5000)
  public void shouldRespondActivatedJobsWhenJobsAreAvailable() {
    // given
    CLIENT_RULE.createJobs(jobType, 2);

    // when
    final ZeebeFuture<ActivateJobsResponse> responseFuture =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(2)
            .send();

    // then
    final ActivateJobsResponse response = responseFuture.join();
    assertThat(response.getJobs()).hasSize(2);
  }

  @Test(timeout = 5000)
  public void shouldRespondNoActivatedJobsWhenNoJobsAvailable() {
    // when
    final ZeebeFuture<ActivateJobsResponse> responseFuture =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send();

    // then
    final ActivateJobsResponse response = responseFuture.join();
    assertThat(response.getJobs()).isEmpty();
  }

  /** This test guarantees that the job activation will only */
  @Test
  public void shouldStreamAllJobsEvenIf() {
    // given
    final var maxMessageSize =
        (int) BROKER_RULE.getBrokerCfg().getNetwork().getMaxMessageSizeInBytes();
    final var processId = Strings.newRandomValidBpmnId();
    final var taskType = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(taskType))
            .endEvent()
            .done();
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process);
    final var client = CLIENT_RULE.getClient();

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(Map.of("foo", "x".repeat(maxMessageSize / 3 + 1)))
        .send()
        .join();
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(Map.of("foo", "x".repeat(maxMessageSize / 3 + 1)))
        .send()
        .join();
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(Map.of("bar", "x".repeat(maxMessageSize / 3 + 1)))
        .send()
        .join();
    final var firstJobBatch =
        client.newActivateJobsCommand().jobType(taskType).maxJobsToActivate(10).send().join();
    final var secondJobBatch =
        client.newActivateJobsCommand().jobType(taskType).maxJobsToActivate(10).send().join();

    // then
    assertThat(firstJobBatch.getJobs()).hasSize(2);
    assertThat(firstJobBatch.getJobs().get(0).getVariablesAsMap()).containsOnlyKeys("foo");
    assertThat(firstJobBatch.getJobs().get(1).getVariablesAsMap()).containsOnlyKeys("foo");
    assertThat(secondJobBatch.getJobs()).hasSize(1);
    assertThat(secondJobBatch.getJobs().get(0).getVariablesAsMap()).containsOnlyKeys("bar");
  }

  private int getMaxDispatcherFragmentLength() {
    final var maxDispatcherFragmentSize =
        (int) BROKER_RULE.getBrokerCfg().getNetwork().getMaxMessageSizeInBytes();
    final var dispatcherFrameLength =
        BitUtil.align(
            LogBufferAppender.claimedBatchLength(1, maxDispatcherFragmentSize)
                - maxDispatcherFragmentSize,
            DataFrameDescriptor.FRAME_ALIGNMENT);
    return maxDispatcherFragmentSize - dispatcherFrameLength;
  }
}

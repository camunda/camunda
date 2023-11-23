/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class UpdateJobTimeoutTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static final RuleChain RULE_CHAIN = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;
  private long jobKey;

  @Before
  public void init() {
    jobType = helper.getJobType();

    final var processDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done());

    CLIENT_RULE.createProcessInstance(processDefinitionKey);

    jobKey = activateJob().getKey();
  }

  @Test
  public void shouldUpdateJobTimeout() {
    // given
    final long newTimeout = 900000;

    // when
    CLIENT_RULE.getClient().newUpdateTimeoutCommand(jobKey).timeout(newTimeout).send().join();

    // then
    assertThat(jobRecords(JobIntent.UPDATE_TIMEOUT).withRecordKey(jobKey).exists()).isTrue();

    final Long updatedDeadline =
        jobRecords(JobIntent.TIMEOUT_UPDATED)
            .withRecordKey(jobKey)
            .findFirst()
            .map(r -> r.getValue().getDeadline())
            .orElse(null);

    assertThat(updatedDeadline).isNotNull();

    assertThat(updatedDeadline)
        .isCloseTo(
            BROKER_RULE.getClock().getCurrentTimeInMillis() + newTimeout,
            within(Duration.ofMillis(100).toMillis()));
  }

  private ActivatedJob activateJob() {
    final var activateResponse =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .timeout(Duration.ofMinutes(10))
            .send()
            .join();

    assertThat(activateResponse.getJobs())
        .describedAs("Expected one job to be activated")
        .hasSize(1);

    return activateResponse.getJobs().get(0);
  }
}

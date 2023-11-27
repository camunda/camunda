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
  private long initialDeadline;

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

    final ActivatedJob job = activateJob();

    initialDeadline = job.getDeadline();
    jobKey = job.getKey();
  }

  @Test
  public void shouldIncreaseJobTimeoutInMillis() {
    // given
    final long timeout = 900000;

    // when
    CLIENT_RULE.getClient().newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutIncreased();
  }

  @Test
  public void shouldDecreaseJobTimeoutInMillis() {
    // given
    final long timeout = 780000;

    // when
    CLIENT_RULE.getClient().newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutDecreased();
  }

  @Test
  public void shouldIncreaseJobTimeoutDuration() {
    // given
    final Duration timeout = Duration.ofMinutes(15);

    // when
    CLIENT_RULE.getClient().newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutIncreased();
  }

  @Test
  public void shouldDecreaseJobTimeoutDuration() {
    // given
    final Duration timeout = Duration.ofMinutes(13);

    // when
    CLIENT_RULE.getClient().newUpdateTimeoutCommand(jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutDecreased();
  }

  private ActivatedJob activateJob() {
    final var activateResponse =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .timeout(Duration.ofMinutes(14))
            .send()
            .join();

    assertThat(activateResponse.getJobs())
        .describedAs("Expected one job to be activated")
        .hasSize(1);

    return activateResponse.getJobs().get(0);
  }

  private void assertTimeoutIncreased() {
    final Long updatedDeadline = retrieveCurrentDeadline();

    assertThat(updatedDeadline).isNotNull();
    assertThat(updatedDeadline).isGreaterThan(initialDeadline);
  }

  private void assertTimeoutDecreased() {
    final Long updatedDeadline = retrieveCurrentDeadline();

    assertThat(updatedDeadline).isNotNull();
    assertThat(updatedDeadline).isLessThan(initialDeadline);
  }

  private Long retrieveCurrentDeadline() {
    assertThat(jobRecords(JobIntent.UPDATE_TIMEOUT).withRecordKey(jobKey).exists()).isTrue();

    return jobRecords(JobIntent.TIMEOUT_UPDATED)
        .withRecordKey(jobKey)
        .findFirst()
        .map(r -> r.getValue().getDeadline())
        .orElse(null);
  }
}

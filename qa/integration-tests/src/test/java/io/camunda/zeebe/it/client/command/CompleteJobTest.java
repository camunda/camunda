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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class CompleteJobTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;
  private ActivatedJob jobEvent;
  private long jobKey;

  @Before
  public void init() {
    jobType = helper.getJobType();
    CLIENT_RULE.createSingleJob(jobType);

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(jobType).handler(jobHandler).open();

    waitUntil(() -> jobHandler.getHandledJobs().size() >= 1);

    jobEvent = jobHandler.getHandledJobs().get(0);
    jobKey = jobEvent.getKey();
  }

  @Test
  public void shouldCompleteJobWithoutVariables() {
    // when
    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @Test
  public void shouldCompleteJobNullVariables() {
    // when
    CLIENT_RULE.getClient().newCompleteCommand(jobKey).variables("null").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @Test
  public void shouldCompleteJobWithVariables() {
    // when
    CLIENT_RULE.getClient().newCompleteCommand(jobKey).variables("{\"foo\":\"bar\"}").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @Test
  public void shouldRejectIfVariablesAreInvalid() {
    // when
    assertThatThrownBy(
            () -> CLIENT_RULE.getClient().newCompleteCommand(jobKey).variables("[]").send().join())
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(
            "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'");
  }

  @Test
  public void shouldRejectIfJobIsAlreadyCompleted() {
    // given
    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();

    // when
    final var expectedMessage =
        String.format("Expected to complete job with key '%d', but no such job was found", jobKey);

    assertThatThrownBy(() -> CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join())
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(expectedMessage);
  }
}

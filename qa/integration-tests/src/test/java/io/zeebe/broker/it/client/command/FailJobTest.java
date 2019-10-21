/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.client.command;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientStatusException;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.BrokerClassRuleHelper;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class FailJobTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;
  private RecordingJobHandler jobHandler;
  private ActivatedJob jobEvent;
  private long jobKey;

  @Before
  public void init() {
    jobType = helper.getJobType();
    CLIENT_RULE.createSingleJob(jobType);

    jobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(jobType).handler(jobHandler).open();

    waitUntil(() -> jobHandler.getHandledJobs().size() >= 1);

    jobEvent = jobHandler.getHandledJobs().get(0);
    jobKey = jobEvent.getKey();
  }

  @Test
  public void shouldFailJobWithRemainingRetries() {
    // when
    CLIENT_RULE.getClient().newFailCommand(jobKey).retries(2).send().join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.FAILED).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasRetries(2).hasErrorMessage("");

    waitUntil(() -> jobHandler.getHandledJobs().size() >= 2);
    final var activatedJob = jobHandler.getHandledJobs().get(1);

    assertThat(activatedJob.getKey()).isEqualTo(jobKey);
    assertThat(activatedJob.getRetries()).isEqualTo(2);
  }

  @Test
  public void shouldFailJobWithErrorMessage() {
    // when
    CLIENT_RULE.getClient().newFailCommand(jobKey).retries(0).errorMessage("test").send().join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.FAILED).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasRetries(0).hasErrorMessage("test");
  }

  @Test
  public void shouldRejectIfJobIsAlreadyCompleted() {
    // given
    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();

    // when
    final var expectedMessage =
        String.format(
            "Expected to fail activated job with key '%d', but it does not exist", jobKey);

    assertThatThrownBy(
            () -> CLIENT_RULE.getClient().newFailCommand(jobKey).retries(1).send().join())
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(expectedMessage);
  }
}

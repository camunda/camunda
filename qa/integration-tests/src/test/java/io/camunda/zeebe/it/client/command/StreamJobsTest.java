/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class StreamJobsTest {
  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  @Test(timeout = 5000)
  public void shouldRespondActivatedJobsWhenJobsAreAvailable() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(2);
    final var jobs = new ArrayList<ActivatedJob>();
    final var stream =
        CLIENT_RULE
            .getClient()
            .newStreamJobsCommand()
            .jobType(jobType)
            .handler(
                (c, j) -> {
                  jobs.add(j);
                  c.newCompleteCommand(j.getKey()).send().join();
                  latch.countDown();
                })
            .workerName("streamer")
            .send();

    // when
    CLIENT_RULE.createJobs(jobType, 2);

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(jobs).hasSize(2);
    stream.cancel(true);
  }
}

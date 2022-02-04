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
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
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
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public final class MultipleClientTest {
  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public final GrpcClientRule client1 = new GrpcClientRule(brokerRule);
  public final GrpcClientRule client2 = new GrpcClientRule(brokerRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(client1).around(client2);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldOpenJobWorkerForDifferentTypes() {
    // given
    final RecordingJobHandler handler1 = new RecordingJobHandler();
    final RecordingJobHandler handler2 = new RecordingJobHandler();
    client1.getClient().newWorker().jobType("foo").handler(handler1).open();
    client2.getClient().newWorker().jobType("bar").handler(handler2).open();

    // when
    final long job1Key = client1.createSingleJob("foo");
    final long job2Key = client1.createSingleJob("bar");

    // then
    waitUntil(() -> handler1.getHandledJobs().size() + handler2.getHandledJobs().size() >= 2);

    assertThat(handler1.getHandledJobs()).hasSize(1);
    assertThat(handler1.getHandledJobs().get(0).getKey()).isEqualTo(job1Key);

    assertThat(handler2.getHandledJobs()).hasSize(1);
    assertThat(handler2.getHandledJobs().get(0).getKey()).isEqualTo(job2Key);
  }
}

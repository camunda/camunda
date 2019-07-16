/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.test.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class ClientReconnectTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Rule public Timeout testTimeout = Timeout.seconds(30);

  @Test
  public void shouldTransparentlyReconnectOnUnexpectedConnectionLoss() {
    // given
    final long initialTaskKey = createJob();

    brokerRule.interruptClientConnections();

    // when
    final long newTaskKey = TestUtil.doRepeatedly(() -> createJob()).until((key) -> key != null);

    // then
    assertThat(newTaskKey).isNotEqualTo(initialTaskKey);
  }

  protected long createJob() {
    return clientRule.createSingleJob(
        "foo",
        b -> {
          b.zeebeTaskHeader("k1", "a").zeebeTaskHeader("k2", "b");
        },
        "{\"variables\":123}");
  }
}

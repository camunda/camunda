/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine;

import static io.camunda.zeebe.protocol.record.intent.MessageIntent.PUBLISH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.camunda.zeebe.test.broker.protocol.commandapi.ExecuteCommandRequestBuilder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class RejectIncompleteCommandsTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();

  private static final CommandApiRule API_RULE = new CommandApiRule(BROKER_RULE::getAtomixCluster);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(API_RULE);

  @Test
  public void shouldFailToPublishMessageWithoutName() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .done();

    assertThatThrownBy(request::sendAndAwait)
        .hasMessageContaining("Property 'name' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutCorrelationKey() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("timeToLive", 1_000)
            .done();

    assertThatThrownBy(request::sendAndAwait)
        .hasMessageContaining("Property 'correlationKey' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutTimeToLive() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .done();

    assertThatThrownBy(() -> request.sendAndAwait())
        .hasMessageContaining("Property 'timeToLive' has no valid value");
  }
}

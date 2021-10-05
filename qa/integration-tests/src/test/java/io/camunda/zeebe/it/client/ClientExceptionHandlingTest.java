/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.grpc.StatusRuntimeException;
import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public final class ClientExceptionHandlingTest {

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public final GrpcClientRule clientRule =
      new GrpcClientRule(
          brokerRule, zeebeClientBuilder -> zeebeClientBuilder.gatewayAddress("localhost:1234"));

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldContainRootCauses() {
    final Throwable throwable = catchThrowable(() -> clientRule.getPartitions());

    assertThat(throwable).isInstanceOf(ClientException.class).hasMessageContaining("io exception");

    final Throwable firstCause = throwable.getCause();
    assertThat(firstCause)
        .isInstanceOf(ExecutionException.class)
        .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception");

    final Throwable secondCause = firstCause.getCause();
    assertThat(secondCause)
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("UNAVAILABLE: io exception");

    final Throwable thirdCause = secondCause.getCause();
    assertThat(thirdCause)
        .hasCauseInstanceOf(ConnectException.class)
        .hasMessageContaining("Connection refused:")
        .hasMessageContaining("localhost")
        .hasMessageContaining(":1234");
  }
}

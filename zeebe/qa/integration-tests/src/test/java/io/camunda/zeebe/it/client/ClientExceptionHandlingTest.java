/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.client.api.command.ClientException;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.grpc.StatusRuntimeException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ClientExceptionHandlingTest {

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private final InetSocketAddress invalidGatewayAddress = SocketUtil.getNextAddress();

  public final GrpcClientRule clientRule =
      new GrpcClientRule(
          brokerRule,
          clientBuilder -> clientBuilder.gatewayAddress(getInvalidGatewayHostAndPort()));

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldContainRootCauses() {
    final Throwable throwable = catchThrowable(clientRule::getPartitions);

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
        .hasMessageContaining(invalidGatewayAddress.getHostName())
        .hasMessageContaining(":" + invalidGatewayAddress.getPort());
  }

  private String getInvalidGatewayHostAndPort() {
    return invalidGatewayAddress.getHostName() + ":" + invalidGatewayAddress.getPort();
  }
}

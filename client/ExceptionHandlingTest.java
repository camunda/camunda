/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public final class ExceptionHandlingTest extends ClientTest {

  @Test
  public void shouldContainCorrectRootCauses() {
    // given
    // https://grpc.io/grpc-java/javadoc/io/grpc/Status.html#withCause-java.lang.Throwable-
    // Status.withCause are only locally (client or server side) but can't be transmitted
    // that is why the last cause will be the StatusException

    final IllegalStateException illegalStateException = new IllegalStateException("Invalid state");
    gatewayService.errorOnRequest(TopologyRequest.class, () -> illegalStateException);

    // when
    final Throwable throwable = catchThrowable(() -> client.newTopologyRequest().send().join());

    assertThat(throwable)
        .isInstanceOf(ClientException.class)
        .hasCauseInstanceOf(ExecutionException.class)
        .hasMessageContaining("Invalid state");

    final Throwable firstCause = throwable.getCause();
    assertThat(firstCause)
        .hasCauseInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("Invalid state");
  }
}

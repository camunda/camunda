/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.CancelProcessInstanceResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import java.time.Duration;
import org.junit.Test;

public final class CancelProcessInstanceTest extends ClientTest {

  @Test
  public void shouldSendCancelCommand() {
    // when
    client.newCancelInstanceCommand(123).send().join();

    // then
    final CancelProcessInstanceRequest request = gatewayService.getLastRequest();
    assertThat(request.getProcessInstanceKey()).isEqualTo(123);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        CancelProcessInstanceRequest.class, () -> new ClientException("Invalid request"));

    assertThatThrownBy(() -> client.newCancelInstanceCommand(123).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newCancelInstanceCommand(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final CancelProcessInstanceCommandStep1 command = client.newCancelInstanceCommand(12);

    // when
    final CancelProcessInstanceResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
  }
}

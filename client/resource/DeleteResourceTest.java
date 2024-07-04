/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeleteResourceResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import java.time.Duration;
import org.junit.Test;

public final class DeleteResourceTest extends ClientTest {

  @Test
  public void shouldSendCommand() {
    // when
    client.newDeleteResourceCommand(123).send().join();

    // then
    final DeleteResourceRequest request = gatewayService.getLastRequest();
    assertThat(request.getResourceKey()).isEqualTo(123);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newDeleteResourceCommand(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final DeleteResourceCommandStep1 command = client.newDeleteResourceCommand(12);

    // when
    final DeleteResourceResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
  }
}

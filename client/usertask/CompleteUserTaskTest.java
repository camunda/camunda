/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.usertask;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public final class CompleteUserTaskTest extends ClientRestTest {

  @Test
  void shouldCompleteUserTask() {
    // when
    client.newUserTaskCompleteCommand(123L).send().join();

    // then
    final UserTaskCompletionRequest request =
        gatewayService.getLastRequest(UserTaskCompletionRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getVariables()).isNull();
  }

  @Test
  void shouldCompleteUserTaskWithAction() {
    // when
    client.newUserTaskCompleteCommand(123L).action("foo").send().join();

    // then
    final UserTaskCompletionRequest request =
        gatewayService.getLastRequest(UserTaskCompletionRequest.class);
    assertThat(request.getAction()).isEqualTo("foo");
    assertThat(request.getVariables()).isNull();
  }

  @Test
  void shouldCompleteUserTaskWithVariables() {
    // when

    client.newUserTaskCompleteCommand(123L).variables(singletonMap("foo", "bar")).send().join();

    // then
    final UserTaskCompletionRequest request =
        gatewayService.getLastRequest(UserTaskCompletionRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getVariables()).isEqualTo(singletonMap("foo", "bar"));
  }

  @Test
  void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getUserTaskCompletionUrl(123L),
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(() -> client.newUserTaskCompleteCommand(123L).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public final class AssignUserTaskTest extends ClientRestTest {

  @Test
  void shouldAssignUserTask() {
    // when
    client.newUserTaskAssignCommand(123L).assignee("foo").send().join();

    // then
    final UserTaskAssignmentRequest request =
        gatewayService.getLastRequest(UserTaskAssignmentRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getAllowOverride()).isNull();
    assertThat(request.getAssignee()).isEqualTo("foo");
  }

  @Test
  void shouldAssignUserTaskWithAction() {
    // when
    client.newUserTaskAssignCommand(123L).action("foo").send().join();

    // then
    final UserTaskAssignmentRequest request =
        gatewayService.getLastRequest(UserTaskAssignmentRequest.class);
    assertThat(request.getAction()).isEqualTo("foo");
    assertThat(request.getAllowOverride()).isNull();
    assertThat(request.getAssignee()).isNull();
  }

  @Test
  void shouldAssignUserTaskWithAllowOverride() {
    // when

    client.newUserTaskAssignCommand(123L).allowOverride(true).send().join();

    // then
    final UserTaskAssignmentRequest request =
        gatewayService.getLastRequest(UserTaskAssignmentRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getAllowOverride()).isTrue();
    assertThat(request.getAssignee()).isNull();
  }

  @Test
  void shouldAssignUserTaskWithAllowOverrideDisabled() {
    // when

    client.newUserTaskAssignCommand(123L).allowOverride(false).send().join();

    // then
    final UserTaskAssignmentRequest request =
        gatewayService.getLastRequest(UserTaskAssignmentRequest.class);
    assertThat(request.getAction()).isNull();
    assertThat(request.getAllowOverride()).isFalse();
    assertThat(request.getAssignee()).isNull();
  }

  @Test
  void shouldRaiseExceptionOnNullAssignee() {
    // when / then
    assertThatThrownBy(() -> client.newUserTaskAssignCommand(123L).assignee(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("assignee must not be null");
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getUserTaskAssignmentUrl(123L),
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(() -> client.newUserTaskAssignCommand(123L).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}

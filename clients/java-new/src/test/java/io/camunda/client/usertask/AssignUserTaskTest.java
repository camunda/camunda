/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
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
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}

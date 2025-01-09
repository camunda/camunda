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

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.UserTaskCompletionRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
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
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}

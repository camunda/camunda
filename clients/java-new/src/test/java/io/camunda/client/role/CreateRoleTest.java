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
package io.camunda.client.role;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.RoleCreateRequest;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class CreateRoleTest extends ClientRestTest {

  public static final String NAME = "Role Name";

  @Test
  void shouldCreateRole() {
    // when
    client.newCreateRoleCommand().name(NAME).send().join();

    // then
    final RoleCreateRequest request = gatewayService.getLastRequest(RoleCreateRequest.class);
    assertThat(request.getName()).isEqualTo(NAME);
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/roles", () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().name(NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionIfRoleNameAlreadyExists() {
    // given
    client.newCreateRoleCommand().name(NAME).send().join();

    gatewayService.errorOnRequest(
        REST_API_PATH + "/roles", () -> new ProblemDetail().title("Conflict").status(409));

    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().name(NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldHandleValidationErrorResponse() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/roles", () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().name(NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}

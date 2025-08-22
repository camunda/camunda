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
package io.camunda.client.user;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.protocol.rest.UserUpdateRequest;
import io.camunda.client.protocol.rest.UserUpdateResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class UpdateUserTest extends ClientRestTest {
  private static final String USERNAME = "username";
  private static final String NAME = "updated user name";
  private static final String EMAIL = "updated_email@email.com";
  private static final String PASSWORD = "updated password";

  @Test
  void shouldUpdateUser() {
    // given
    gatewayService.onUpdateUserRequest(USERNAME, Instancio.create(UserUpdateResult.class));

    // when
    client.newUpdateUserCommand(USERNAME).name(NAME).email(EMAIL).password(PASSWORD).send().join();

    // then
    final UserUpdateRequest request = gatewayService.getLastRequest(UserUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(NAME);
    assertThat(request.getEmail()).isEqualTo(EMAIL);
    assertThat(request.getPassword()).isEqualTo(PASSWORD);

    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath).isEqualTo(REST_API_PATH + "/users/" + USERNAME);
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.PUT);
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateUserCommand(USERNAME)
                    .name(null)
                    .email("new_email@email.com")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateUserCommand(USERNAME)
                    .name("")
                    .email("new_email@email.com")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullEmail() {
    // when / then
    assertThatThrownBy(
            () -> client.newUpdateUserCommand(USERNAME).name(NAME).email(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("email must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyEmail() {
    // when / then
    assertThatThrownBy(
            () -> client.newUpdateUserCommand(USERNAME).name(NAME).email("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("email must not be empty");
  }
}

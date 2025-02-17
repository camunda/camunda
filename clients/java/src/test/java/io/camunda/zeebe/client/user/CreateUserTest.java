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
package io.camunda.zeebe.client.user;

import static io.camunda.zeebe.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.UserRequest;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class CreateUserTest extends ClientRestTest {

  public static final String USERNAME = "username";
  public static final String NAME = "name";
  public static final String EMAIL = "email@example.com";
  public static final String PASSWORD = "password";

  @Test
  void shouldCreateUser() {
    // when
    client
        .newUserCreateCommand()
        .username(USERNAME)
        .name(NAME)
        .email(EMAIL)
        .password(PASSWORD)
        .send()
        .join();

    // then
    final UserRequest request = gatewayService.getLastRequest(UserRequest.class);
    assertThat(request.getUsername()).isEqualTo(USERNAME);
    assertThat(request.getName()).isEqualTo(NAME);
    assertThat(request.getEmail()).isEqualTo(EMAIL);
    assertThat(request.getPassword()).isEqualTo(PASSWORD);
  }

  @Test
  void shouldRaiseExceptionOnNullUsername() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .name(NAME)
                    .email(EMAIL)
                    .password(PASSWORD)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullEmail() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .username(USERNAME)
                    .name(NAME)
                    .password(PASSWORD)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("email must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .username(USERNAME)
                    .email(EMAIL)
                    .password(PASSWORD)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnNullPassword() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .username(USERNAME)
                    .name(NAME)
                    .email(EMAIL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("password must not be null");
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/users", () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .username(USERNAME)
                    .name(NAME)
                    .email(EMAIL)
                    .password(PASSWORD)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}

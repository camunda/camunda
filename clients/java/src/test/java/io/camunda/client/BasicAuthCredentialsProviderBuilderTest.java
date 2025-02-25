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
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.impl.CamundaClientEnvironmentVariables;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.Environment;
import io.camunda.client.impl.util.EnvironmentExtension;
import io.camunda.client.util.TestCredentialsApplier;
import io.camunda.client.util.TestCredentialsApplier.Credential;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EnvironmentExtension.class)
public class BasicAuthCredentialsProviderBuilderTest {

  @Test
  void shouldFailWithNullUsername() {
    // given
    final BasicAuthCredentialsProviderBuilder builder = new BasicAuthCredentialsProviderBuilder();

    // when
    builder.password("password");

    // then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            "Username cannot be null or empty. Ensure the username is set explicitly, or through the environment variable 'CAMUNDA_BASIC_AUTH_USERNAME'.");
  }

  @Test
  void shouldFailWithEmptyUsername() {
    // given
    final BasicAuthCredentialsProviderBuilder builder = new BasicAuthCredentialsProviderBuilder();

    // when
    builder.username("").password("password");

    // then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            "Username cannot be null or empty. Ensure the username is set explicitly, or through the environment variable 'CAMUNDA_BASIC_AUTH_USERNAME'.");
  }

  @Test
  void shouldFailWithNullPassword() {
    // given
    final BasicAuthCredentialsProviderBuilder builder = new BasicAuthCredentialsProviderBuilder();

    // when
    builder.username("username");

    // then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            "Password cannot be null or empty. Ensure the password is set explicitly, or through the environment variable 'CAMUNDA_BASIC_AUTH_PASSWORD'.");
  }

  @Test
  void shouldUseEnvironmentVariableByDefault() {
    // given
    final Environment system = Environment.system();
    system.put(CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_USERNAME, "foo");
    system.put(CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_PASSWORD, "bar");
    final BasicAuthCredentialsProviderBuilder builder = new BasicAuthCredentialsProviderBuilder();
    final TestCredentialsApplier applier = new TestCredentialsApplier();

    // when
    final BasicAuthCredentialsProvider provider = builder.build();
    provider.applyCredentials(applier);

    // then
    assertThat(applier.getCredentials())
        .containsExactly(
            new Credential("Authorization", "Basic " + base64EncodeCredentials("foo", "bar")));
  }

  @Test
  void shouldNotApplyEnvironmentOverrides() {
    // given
    final Environment system = Environment.system();
    system.put(CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_USERNAME, "foo");
    system.put(CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_PASSWORD, "bar");
    final BasicAuthCredentialsProviderBuilder builder = new BasicAuthCredentialsProviderBuilder();

    // when
    builder.applyEnvironmentOverrides(false);

    // then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            "Username cannot be null or empty. Ensure the username is set explicitly, or through the environment variable 'CAMUNDA_BASIC_AUTH_USERNAME'.");
  }

  private String base64EncodeCredentials(final String username, final String password) {
    return Base64.getEncoder()
        .encodeToString(String.format("%s:%s", username, password).getBytes());
  }
}

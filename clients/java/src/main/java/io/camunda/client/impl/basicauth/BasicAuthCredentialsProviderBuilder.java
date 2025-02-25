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
package io.camunda.client.impl.basicauth;

import io.camunda.client.impl.BuilderUtils;
import io.camunda.client.impl.CamundaClientEnvironmentVariables;

public final class BasicAuthCredentialsProviderBuilder {
  private String username;
  private String password;
  private boolean applyEnvironmentOverrides = true;

  /**
   * Username to be used for basic authentication. This can also be set using the environment
   * variable 'CAMUNDA_BASIC_AUTH_USERNAME'. If both are set the environment variable will take
   * precedence unless disabling environment by setting it to false using the {@link
   * #applyEnvironmentOverrides}' method.
   */
  public BasicAuthCredentialsProviderBuilder username(final String username) {
    this.username = username;
    return this;
  }

  /**
   * Password to be used for basic authentication. This can also be set using the environment
   * variable 'CAMUNDA_BASIC_AUTH_PASSWORD'. If both are set the environment variable will take
   * precedence unless disabling environment by setting it to false using the {@link
   * #applyEnvironmentOverrides}' method.
   */
  public BasicAuthCredentialsProviderBuilder password(final String password) {
    this.password = password;
    return this;
  }

  /**
   * Whether to apply environment overrides to the builder. If set to true environment variables
   * will take precedence over values explicitly set in this builder. If set to false the
   * environment variables will not be used at all. If they do exist they will be ignored.
   */
  public BasicAuthCredentialsProviderBuilder applyEnvironmentOverrides(
      final boolean applyEnvironmentOverrides) {
    this.applyEnvironmentOverrides = applyEnvironmentOverrides;
    return this;
  }

  public BasicAuthCredentialsProvider build() {
    if (applyEnvironmentOverrides) {
      BuilderUtils.applyEnvironmentValueIfNotNull(
          this::username, CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_USERNAME);
      BuilderUtils.applyEnvironmentValueIfNotNull(
          this::password, CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_PASSWORD);
    }

    validate();

    return new BasicAuthCredentialsProvider(username, password);
  }

  private void validate() {
    if (username == null || username.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Username cannot be null or empty. Ensure the username is set explicitly, or through the environment variable '%s'.",
              CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_USERNAME));
    }
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Password cannot be null or empty. Ensure the password is set explicitly, or through the environment variable '%s'.",
              CamundaClientEnvironmentVariables.BASIC_AUTH_ENV_PASSWORD));
    }
  }
}

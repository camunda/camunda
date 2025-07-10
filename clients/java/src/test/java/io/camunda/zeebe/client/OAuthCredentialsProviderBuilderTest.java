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
package io.camunda.zeebe.client;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class OAuthCredentialsProviderBuilderTest {

  private final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

  @Test
  void shouldFailWithNoClientId() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder.audience("a").clientSecret("b").authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith("Expected valid clientId but none was provided.");
  }

  @Test
  void shouldFailWithNoClientSecret() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder.audience("a").clientId("b").authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            "Either clientSecret or certificate-based authentication must be configured");
  }

  @Test
  void shouldFailWithMalformedServerUrl() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder.audience("a").clientId("b").clientSecret("c").authorizationServerUrl("someServerUrl");

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(MalformedURLException.class);
  }

  @Test
  void shouldFailIfSpecifiedCacheIsDir(final @TempDir Path tmpDir) {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .credentialsCachePath(tmpDir.toString())
        .authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected specified credentials cache to be a file but found directory instead.");
  }

  @Test
  void shouldThrowExceptionIfTimeoutIsZero() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .readTimeout(Duration.ZERO)
        .authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build)
        .hasMessageContaining(
            "ReadTimeout timeout is 0 milliseconds, "
                + "expected timeout to be a positive number of milliseconds smaller than 2147483647.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowExceptionIfTimeoutTooLarge() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .readTimeout(Duration.ofDays(1_000_000))
        .authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build)
        .hasMessageContaining(
            "ReadTimeout timeout is 86400000000000 milliseconds, "
                + "expected timeout to be a positive number of milliseconds smaller than 2147483647.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldBuildWithResourceParameter() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .resource("https://api.example.com")
        .authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  void shouldBuildWithoutResourceParameter() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder.audience("a").clientId("b").clientSecret("c").authorizationServerUrl("http://some.url");

    // then
    assertThatCode(builder::build).doesNotThrowAnyException();
  }
}

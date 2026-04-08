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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        .hasMessageEndingWith(
            String.format(OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "client id"));
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
  void shouldFailWhenTrustStoreCannotBeLocated() throws IOException {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .authorizationServerUrl("http://some.url")
        .truststorePath(Paths.get("/does/not/exist"))
        .keystorePath(Files.createTempFile("test-", ".jks").toAbsolutePath());

    // then
    assertThatCode(builder::build)
        .hasMessageContaining("Truststore path does not exist")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWhenKeyStoreCannotBeLocated() throws IOException {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .authorizationServerUrl("http://some.url")
        .keystorePath(Paths.get("/does/not/exist"))
        .truststorePath(Files.createTempFile("test-", ".jks").toAbsolutePath());

    // then
    assertThatCode(builder::build)
        .hasMessageContaining("Keystore path does not exist")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNegativeProactiveTokenRefreshThreshold() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .audience("a")
            .clientId("b")
            .clientSecret("c")
            .authorizationServerUrl("http://some.url")
            .proactiveTokenRefreshThreshold(Duration.ofSeconds(-1));

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Proactive token refresh threshold")
        .hasMessageContaining("expected a positive duration");
  }

  @Test
  void shouldRejectZeroProactiveTokenRefreshThreshold() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .audience("a")
            .clientId("b")
            .clientSecret("c")
            .authorizationServerUrl("http://some.url")
            .proactiveTokenRefreshThreshold(Duration.ZERO);

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Proactive token refresh threshold")
        .hasMessageContaining("expected a positive duration");
  }

  @Test
  void shouldRejectProactiveTokenRefreshThresholdEqualToGracePeriod() {
    // given — exactly equal to the grace period: not strictly larger, so must be rejected
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .audience("a")
            .clientId("b")
            .clientSecret("c")
            .authorizationServerUrl("http://some.url")
            .proactiveTokenRefreshThreshold(
                io.camunda.client.impl.CamundaClientCredentials.EXPIRY_GRACE_PERIOD);

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be strictly larger than the expiry grace period");
  }

  @Test
  void shouldRejectProactiveTokenRefreshThresholdBelowGracePeriod() {
    // given — below the 5s grace period
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .audience("a")
            .clientId("b")
            .clientSecret("c")
            .authorizationServerUrl("http://some.url")
            .proactiveTokenRefreshThreshold(Duration.ofSeconds(1));

    // then
    assertThatCode(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be strictly larger than the expiry grace period");
  }

  @Test
  void shouldApplyDefaultProactiveTokenRefreshThresholdWhenUnset() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder().audience("a").clientId("b").clientSecret("c");

    // when
    builder.build();

    // then
    assertThat(builder.getProactiveTokenRefreshThreshold())
        .isEqualTo(OAuthCredentialsProviderBuilder.DEFAULT_PROACTIVE_TOKEN_REFRESH_THRESHOLD);
  }

  @Test
  void shouldAcceptCustomProactiveTokenRefreshThreshold() {
    // given
    final Duration custom = Duration.ofSeconds(90);
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .audience("a")
            .clientId("b")
            .clientSecret("c")
            .authorizationServerUrl("http://some.url")
            .proactiveTokenRefreshThreshold(custom);

    // when
    builder.build();

    // then
    assertThat(builder.getProactiveTokenRefreshThreshold()).isEqualTo(custom);
  }
}

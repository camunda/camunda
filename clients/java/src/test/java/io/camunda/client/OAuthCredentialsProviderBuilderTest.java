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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.DEFAULT_AUTHZ_SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
public final class OAuthCredentialsProviderBuilderTest {

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
  void shouldDetectTokenEndpointFromWellKnownConfigurationUrl(
      final WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        get("/.well-known/openid-configuration")
            .willReturn(ok().withBody("{\"token_endpoint\": \"http://token-endpoint\"}")));
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .wellKnownConfigurationUrl(
            "http://localhost:"
                + wmRuntimeInfo.getHttpPort()
                + "/.well-known/openid-configuration");
    builder.build();
    final String authorizationServerUrl = builder.getAuthorizationServer().toString();
    assertThat(authorizationServerUrl).isEqualTo("http://token-endpoint");
  }

  @Test
  void shouldDetectTokenEndpointFromIssuerUrl(final WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        get("/.well-known/openid-configuration")
            .willReturn(ok().withBody("{\"token_endpoint\": \"http://token-endpoint\"}")));
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();
    builder
        .audience("a")
        .clientId("b")
        .clientSecret("c")
        .issuerUrl("http://localhost:" + wmRuntimeInfo.getHttpPort());
    builder.build();
    final String authorizationServerUrl = builder.getAuthorizationServer().toString();
    assertThat(authorizationServerUrl).isEqualTo("http://token-endpoint");
  }

  @Test
  void shouldFailWhenWellKnownConfigurationIsWrong() {
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();
    builder.audience("a").clientId("b").clientSecret("c").issuerUrl("http://some-issuer");
    // then
    assertThatCode(builder::build)
        .hasMessageContaining("Failed to retrieve well known configuration")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldUseDefaultAuthorizationServerUrl() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder().audience("a").clientId("b").clientSecret("c");
    // when
    builder.build();
    // then
    final String authorizationServerUrl = builder.getAuthorizationServer().toString();
    assertThat(authorizationServerUrl).isEqualTo(DEFAULT_AUTHZ_SERVER);
  }
}

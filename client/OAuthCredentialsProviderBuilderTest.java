/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
        .hasMessageEndingWith(
            String.format(OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "client id"));
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
            String.format(OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "client secret"));
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
}

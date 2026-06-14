/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConfigValidatorTest {

  private static OAuthConfig validOAuthConfig() {
    return new OAuthConfig()
        .setClientId("client-id")
        .setClientSecret("client-secret")
        .setAuthorizationServerUrl("https://auth.example.com/oauth/token");
  }

  private static Config validBaseConfig() {
    return new Config().setUrl("http://example.com");
  }

  @Test
  void shouldRejectOAuthMissingClientId() {
    // given
    final Config config = validBaseConfig().setOauth(validOAuthConfig().setClientId(null));

    // when / then
    assertThatThrownBy(() -> ConfigValidator.validate(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId");
  }

  @Test
  void shouldRejectOAuthMissingClientSecret() {
    // given
    final Config config = validBaseConfig().setOauth(validOAuthConfig().setClientSecret(""));

    // when / then
    assertThatThrownBy(() -> ConfigValidator.validate(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientSecret");
  }

  @Test
  void shouldRejectOAuthMissingAuthorizationServerUrl() {
    // given
    final Config config =
        validBaseConfig().setOauth(validOAuthConfig().setAuthorizationServerUrl(null));

    // when / then
    assertThatThrownBy(() -> ConfigValidator.validate(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authorizationServerUrl");
  }

  @Test
  void shouldRejectBothApiKeyAndOAuthConfigured() {
    // given
    final Config config = validBaseConfig().setApiKey("key").setOauth(validOAuthConfig());

    // when / then
    assertThatThrownBy(() -> ConfigValidator.validate(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only one");
  }

  @Test
  void shouldAcceptValidOAuthConfig() {
    // given
    final Config config = validBaseConfig().setOauth(validOAuthConfig());

    // when / then
    assertThatCode(() -> ConfigValidator.validate(config)).doesNotThrowAnyException();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.appint.config.Config;
import io.camunda.exporter.appint.config.OAuthConfig;
import io.camunda.exporter.appint.transport.Authentication;
import io.camunda.exporter.appint.transport.HttpTransportImpl;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class SubscriptionFactoryAuthTest {

  @Test
  void shouldBuildOAuthAuthenticationWhenOAuthConfigured() throws Exception {
    // given
    final Config config =
        new Config()
            .setUrl("http://example.com")
            .setOauth(
                new OAuthConfig()
                    .setClientId("id")
                    .setClientSecret("secret")
                    .setAuthorizationServerUrl("https://auth.example.com/oauth/token"));

    // when
    final var subscription = SubscriptionFactory.createDefault(config, position -> {});

    // then
    final Authentication auth = extractAuthentication(subscription);
    assertThat(auth).isInstanceOf(Authentication.OAuth.class);
    subscription.close();
  }

  @Test
  void shouldFallBackToApiKeyWhenOnlyApiKeyConfigured() throws Exception {
    // given
    final Config config = new Config().setUrl("http://example.com").setApiKey("key");

    // when
    final var subscription = SubscriptionFactory.createDefault(config, position -> {});

    // then
    final Authentication auth = extractAuthentication(subscription);
    assertThat(auth).isInstanceOf(Authentication.ApiKey.class);
    assertThat(((Authentication.ApiKey) auth).apiKey()).isEqualTo("key");
    subscription.close();
  }

  @Test
  void shouldUseNoneWhenNeitherConfigured() throws Exception {
    // given
    final Config config = new Config().setUrl("http://example.com");

    // when
    final var subscription = SubscriptionFactory.createDefault(config, position -> {});

    // then
    final Authentication auth = extractAuthentication(subscription);
    assertThat(auth).isInstanceOf(Authentication.None.class);
    subscription.close();
  }

  private static Authentication extractAuthentication(final Subscription<?> subscription)
      throws Exception {
    final Field transportField = Subscription.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    final HttpTransportImpl transport = (HttpTransportImpl) transportField.get(subscription);
    final Field authField = HttpTransportImpl.class.getDeclaredField("authentication");
    authField.setAccessible(true);
    return (Authentication) authField.get(transport);
  }
}

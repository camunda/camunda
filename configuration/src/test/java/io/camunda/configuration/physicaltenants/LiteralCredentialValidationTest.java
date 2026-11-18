/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class LiteralCredentialValidationTest {

  private static final String PASSWORD = "data.secondary-storage.elasticsearch.password";
  private static final String TENANT = "camunda.physical-tenants.tenanta.";

  @Test
  void shouldWarnAboutEachLiteralCredentialOfATenantsOwnProperties() {
    // given
    final var properties =
        Map.<String, Object>of(
            "camunda." + PASSWORD, "root-password",
            TENANT + "data.primary-storage.backup.s3.secret-key", "tenant-key");

    // when
    try (final var logs = LogCapturer.capturing(LiteralCredentialValidation.class, Level.WARN)) {
      resolve(properties);

      // then
      assertThat(logs.messagesAt(Level.WARN))
          .hasSize(2)
          .anySatisfy(warning -> assertThat(warning).startsWith("camunda." + PASSWORD + " holds"))
          .anySatisfy(
              warning ->
                  assertThat(warning)
                      .startsWith(TENANT + "data.primary-storage.backup.s3.secret-key holds"))
          .allSatisfy(warning -> assertThat(warning).doesNotContain("root-password", "tenant-key"));
    }
  }

  @Test
  void shouldNotWarnAboutReferencesBlanksOrPropertiesThatArentCredentials() {
    // given
    final var properties =
        Map.<String, Object>of(
            "camunda.secrets.internal-config.file.path",
            "/etc/secrets",
            "camunda." + PASSWORD,
            "secret:es-password",
            "camunda.data.secondary-storage.elasticsearch.username",
            "camunda",
            "camunda.data.primary-storage.backup.s3.secret-key",
            "");

    // when
    try (final var logs = LogCapturer.capturing(LiteralCredentialValidation.class, Level.WARN)) {
      resolve(properties);

      // then
      assertThat(logs.messagesAt(Level.WARN)).isEmpty();
    }
  }

  @Test
  void shouldRejectALiteralCredentialWhenSetTo() {
    // given
    final var properties =
        Map.<String, Object>of(
            "camunda.secrets.reject-literal-credentials",
            "true",
            TENANT + PASSWORD,
            "tenant-password");

    // when / then
    assertThatThrownBy(() -> resolve(properties))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining(TENANT + PASSWORD)
        .hasMessageNotContaining("tenant-password");
  }

  private static void resolve(final Map<String, Object> properties) {
    final var all = new HashMap<>(properties);
    if (properties.keySet().stream().anyMatch(key -> key.startsWith(TENANT))) {
      all.put(TENANT + "security.initialization.default-roles.admin.users[0]", "tenanta-admin");
      all.put(TENANT + "data.secondary-storage.elasticsearch.index-prefix", "tenanta");
    }
    final var environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    PhysicalTenantResolver.of(environment, camunda);
  }
}

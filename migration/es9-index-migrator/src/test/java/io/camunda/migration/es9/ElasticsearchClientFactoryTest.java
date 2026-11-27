/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

final class ElasticsearchClientFactoryTest {

  private static StandardEnvironment environmentOf(final Map<String, Object> properties) {
    final var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @Test
  void shouldTakeConnectionSettingsFromTheSecondaryStorageProperties() {
    // given
    final var environment =
        environmentOf(
            Map.of(
                "camunda.data.secondary-storage.type", "elasticsearch",
                "camunda.data.secondary-storage.elasticsearch.url", "https://es.example:9201",
                "camunda.data.secondary-storage.elasticsearch.username", "oc-user",
                "camunda.data.secondary-storage.elasticsearch.password", "oc-secret",
                "camunda.data.secondary-storage.elasticsearch.security.enabled", "true",
                "camunda.data.secondary-storage.elasticsearch.security.certificate-path",
                    "/certs/ca.pem",
                "camunda.data.secondary-storage.elasticsearch.security.verify-hostname", "false"));

    // when
    final var configuration = ElasticsearchClientFactory.connectConfiguration(environment);

    // then
    assertThat(configuration.getUrl()).isEqualTo("https://es.example:9201");
    assertThat(configuration.getUsername()).isEqualTo("oc-user");
    assertThat(configuration.getPassword()).isEqualTo("oc-secret");
    assertThat(configuration.getSecurity().isEnabled()).isTrue();
    assertThat(configuration.getSecurity().getCertificatePath()).isEqualTo("/certs/ca.pem");
    assertThat(configuration.getSecurity().isVerifyHostname()).isFalse();
  }

  @Test
  void shouldAcceptThePropertiesAsEnvironmentVariables() {
    // given
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                "systemEnvironment",
                Map.of(
                    "CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", "http://es-env:9200",
                    "CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_USERNAME", "env-user")));

    // when
    final var configuration = ElasticsearchClientFactory.connectConfiguration(environment);

    // then
    assertThat(configuration.getUrl()).isEqualTo("http://es-env:9200");
    assertThat(configuration.getUsername()).isEqualTo("env-user");
  }

  @Test
  void shouldIgnoreLegacyProperties() {
    // given
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                "systemEnvironment",
                Map.of(
                    "CAMUNDA_DATABASE_URL", "http://legacy:9200",
                    "CAMUNDA_DATABASE_MAXCONNECTIONS", "20")));

    // when
    final var configuration = ElasticsearchClientFactory.connectConfiguration(environment);

    // then
    assertThat(configuration.getUrl()).isEqualTo("http://localhost:9200");
    assertThat(configuration.getMaxConnections()).isNull();
  }

  @Test
  void shouldRejectAnotherSecondaryStorageType() {
    // given
    final var environment =
        environmentOf(Map.of("camunda.data.secondary-storage.type", "opensearch"));

    // when / then
    assertThatThrownBy(() -> ElasticsearchClientFactory.connectConfiguration(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opensearch");
  }
}

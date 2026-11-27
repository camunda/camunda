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
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.ilm.GetIlmStatusResponse;
import co.elastic.clients.elasticsearch.security.IndexPrivilege;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the client against a real Elasticsearch 8.19, the version the Job runs on, as the user the
 * Orchestration Cluster connects with.
 */
final class ElasticsearchClientIT {

  // The version the Job meets in production
  private static final DockerImageName ES_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag("8.19.23");
  private static final String SUPERUSER = "elastic";
  private static final String SUPERUSER_PASSWORD = "superuser-password";
  private static final String OC_USER = "oc-user";
  private static final String OC_PASSWORD = "oc-user-password";

  private static ElasticsearchContainer elasticsearch;

  @BeforeAll
  static void startElasticsearchAndCreateTheOcUser() throws IOException {
    elasticsearch =
        TestSearchContainers.createElasticsearchContainer(ES_IMAGE)
            .withEnv("xpack.security.enabled", "true")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withPassword(SUPERUSER_PASSWORD);
    elasticsearch.start();

    try (final var superuser = client(SUPERUSER, SUPERUSER_PASSWORD)) {
      // the container is reported as started before the built-in users can authenticate
      await("the superuser can authenticate")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .until(() -> superuser.security().authenticate().username().equals(SUPERUSER));
      // the role the operator gives the Orchestration Cluster user
      superuser
          .security()
          .putRole(
              r ->
                  r.name("oc-role")
                      .cluster("manage", "manage_ilm")
                      .indices(i -> i.names("*").privileges(IndexPrivilege.All)));
      superuser.security().putUser(u -> u.username(OC_USER).password(OC_PASSWORD).roles("oc-role"));
    }
  }

  @AfterAll
  static void stopElasticsearch() {
    if (elasticsearch != null) {
      elasticsearch.stop();
    }
  }

  @Test
  void shouldStopAndStartIlmAsTheOcUser() throws IOException {
    try (final var client = client(OC_USER, OC_PASSWORD)) {
      // given
      assertThat(client.ilm().getStatus().operationMode().jsonValue()).isEqualTo("RUNNING");

      // when
      client.ilm().stop();

      // then
      await("ILM is stopped")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(ilmMode(client)).isEqualTo("STOPPED"));

      // when
      client.ilm().start();

      // then
      await("ILM is running again")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(ilmMode(client)).isEqualTo("RUNNING"));
    }
  }

  @Test
  void shouldReadTheSystemFeaturesStatusAsTheOcUser() throws IOException {
    try (final var client = client(OC_USER, OC_PASSWORD)) {
      // when
      final var features = client.migration().getFeatureUpgradeStatus().features();

      // then
      assertThat(features)
          .anySatisfy(
              feature -> {
                assertThat(feature.featureName()).isEqualTo("tasks");
                assertThat(feature.migrationStatus().jsonValue()).isEqualTo("NO_MIGRATION_NEEDED");
              });
    }
  }

  @Test
  void shouldBeRejectedWithoutTheCompatibilityHeader() throws IOException {
    // given a client that asks for the version 9 API, as the default client does
    final var configuration = connectConfiguration(OC_USER, OC_PASSWORD);

    try (final var client = new ElasticsearchConnector(configuration).createClient()) {
      // when / then
      assertThatThrownBy(() -> client.ilm().getStatus())
          .isInstanceOf(ElasticsearchException.class)
          .hasMessageContaining("Accept version must be either version 8 or 7, but found 9");
    }
  }

  private static String ilmMode(final ElasticsearchClient client) throws IOException {
    final GetIlmStatusResponse status = client.ilm().getStatus();
    return status.operationMode().jsonValue();
  }

  private static ElasticsearchClient client(final String username, final String password) {
    return ElasticsearchClientFactory.create(environment(username, password));
  }

  private static ConnectConfiguration connectConfiguration(
      final String username, final String password) {
    return ElasticsearchClientFactory.connectConfiguration(environment(username, password));
  }

  private static StandardEnvironment environment(final String username, final String password) {
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.data.secondary-storage.type",
                    "elasticsearch",
                    "camunda.data.secondary-storage.elasticsearch.url",
                    "http://" + elasticsearch.getHttpHostAddress(),
                    "camunda.data.secondary-storage.elasticsearch.username",
                    username,
                    "camunda.data.secondary-storage.elasticsearch.password",
                    password)));
    return environment;
  }
}

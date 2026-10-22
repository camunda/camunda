/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The registry of clusters, and the credentials of each. */
final class ClustersTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final StoreConfig EAST =
      new StoreConfig(
          Type.S3,
          null,
          "east-bucket",
          "plane",
          "us-east-1",
          URI.create("http://minio:9000"),
          "key",
          "very-secret",
          true);
  private static final StoreConfig WEST = StoreConfig.filesystem(Path.of("/data/west"));
  private static final String EAST_JSON =
      """
      {"type":"s3","bucket":"east-bucket","basePath":"plane","region":"us-east-1",
       "endpoint":"http://minio:9000","accessKeyId":"key","secretAccessKey":"%s",
       "forcePathStyle":true}""";
  private static final String WEST_JSON =
      "{\"type\":\"filesystem\",\"path\":\"/data/west\",\"forcePathStyle\":false}";

  @TempDir private Path directory;
  private ServiceFixture fixture;

  @BeforeEach
  void startService() throws Exception {
    fixture =
        ServiceFixture.start(
            new FileSystemStore(directory.resolve("home")),
            config -> config.withClusters(Map.of("east", EAST, "west", WEST)));
  }

  @AfterEach
  void stopService() throws Exception {
    fixture.close();
  }

  @Test
  void shouldListTheClustersWithTheSecretsMasked() throws Exception {
    // when
    final var response = fixture.get("/v1/clusters");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
    assertThat(MAPPER.readTree(response.body()))
        .isEqualTo(
            MAPPER.readTree(
                "{\"clusters\":{\"east\":%s,\"west\":%s}}"
                    .formatted(EAST_JSON.formatted("***"), WEST_JSON)));
    assertThat(response.body()).doesNotContain("very-secret");
  }

  @Test
  void shouldListNoClustersByDefault() throws Exception {
    // given
    try (final var empty = ServiceFixture.start(new FileSystemStore(directory.resolve("empty")))) {
      // when
      final var response = empty.get("/v1/clusters");

      // then
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("{\"clusters\":{}}");
    }
  }

  @Test
  void shouldAnswerTheStoreOfAClusterWithItsSecret() throws Exception {
    // when
    final var east = fixture.get("/v1/credentials?cluster=east");
    final var west = fixture.get("/v1/credentials?cluster=west");

    // then
    assertThat(east.statusCode()).isEqualTo(200);
    assertThat(east.headers().firstValue("Content-Type")).contains("application/json");
    assertThat(MAPPER.readTree(east.body()))
        .isEqualTo(MAPPER.readTree(EAST_JSON.formatted("very-secret")));
    assertThat(west.statusCode()).isEqualTo(200);
    assertThat(MAPPER.readTree(west.body())).isEqualTo(MAPPER.readTree(WEST_JSON));
  }

  @ParameterizedTest
  @ValueSource(strings = {"north", "EAST", "", "east/"})
  void shouldAnswer404ToAClusterThatIsNotRegistered(final String cluster) throws Exception {
    // when
    final var response = fixture.get("/v1/credentials?cluster=" + cluster.replace("/", "%2F"));

    // then
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(MAPPER.readTree(response.body()).get("error").asText())
        .isEqualTo("Expected the cluster '%s' to be registered, but found none".formatted(cluster));
  }

  @ParameterizedTest
  @ValueSource(strings = {"my+cluster%2F1", "my%20cluster%2F1"})
  void shouldTakeTheClusterPercentDecoded(final String encoded) throws Exception {
    // given
    try (final var named =
        ServiceFixture.start(
            new FileSystemStore(directory.resolve("named")),
            config -> config.withClusters(Map.of("my cluster/1", WEST)))) {
      // when
      final var response = named.get("/v1/credentials?cluster=" + encoded);

      // then
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(MAPPER.readTree(response.body())).isEqualTo(MAPPER.readTree(WEST_JSON));
    }
  }

  @Test
  void shouldAnswerTheConfiguredCredentialsWithoutACluster() throws Exception {
    // given
    try (final var configured =
        ServiceFixture.start(
            new FileSystemStore(directory.resolve("configured")),
            config ->
                config
                    .withCredentialsJson("{\"token\":\"t\"}")
                    .withClusters(Map.of("east", EAST)))) {
      // when
      final var plain = configured.get("/v1/credentials");
      final var other = configured.get("/v1/credentials?other=east");

      // then
      assertThat(plain.statusCode()).isEqualTo(200);
      assertThat(plain.body()).isEqualTo("{\"token\":\"t\"}");
      assertThat(other.body()).isEqualTo("{\"token\":\"t\"}");
    }
  }

  @Test
  void shouldAnswer400ToMoreThanOneCluster() throws Exception {
    // when
    final var response = fixture.get("/v1/credentials?cluster=east&cluster=west");

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(MAPPER.readTree(response.body()).get("error").asText())
        .isEqualTo("Expected at most one parameter cluster, but got 2 of them");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ServiceClustersTest {

  private static final String EAST_JSON =
      """
      {"type":"s3","bucket":"east-bucket","basePath":"plane","region":"us-east-1",
       "endpoint":"http://minio:9000","accessKeyId":"key","secretAccessKey":"very-secret",
       "forcePathStyle":true,"unknown":"skipped"}""";
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
  private static final String WEST_JSON =
      "{\"type\":\"filesystem\",\"path\":\"/data/west\",\"forcePathStyle\":false}";
  private static final StoreConfig WEST = StoreConfig.filesystem(Path.of("/data/west"));

  private StubService service;

  @BeforeEach
  void startService() throws IOException {
    service = StubService.start();
  }

  @AfterEach
  void stopService() {
    service.close();
  }

  @Test
  void shouldFetchTheStoreOfEveryCluster() throws Exception {
    // given
    service.clusters(Map.of("east", EAST_JSON, "west", WEST_JSON));

    // when
    final var stores = ServiceClusters.fetch(service.uri());

    // then
    assertThat(stores).containsOnly(entry("east", EAST), entry("west", WEST));
    assertThat(service.requests())
        .containsExactlyInAnyOrder(
            "/v1/clusters", "/v1/credentials?cluster=east", "/v1/credentials?cluster=west");
  }

  @Test
  void shouldFetchNoStoresFromAServiceWithoutClusters() throws Exception {
    // given
    service.clusters(Map.of());

    // when
    final var stores = ServiceClusters.fetch(service.uri());

    // then
    assertThat(stores).isEmpty();
    assertThat(service.requests()).containsExactly("/v1/clusters");
  }

  @Test
  void shouldIgnoreATrailingSlashOfTheServiceUri() throws Exception {
    // given
    service.clusters(Map.of("west", WEST_JSON));

    // when
    final var stores = ServiceClusters.fetch(URI.create(service.uri() + "/"));

    // then
    assertThat(stores).containsOnly(entry("west", WEST));
  }

  @Test
  void shouldAskForAClusterByItsEncodedId() throws Exception {
    // given
    service.clusters(Map.of("my cluster/1", WEST_JSON));

    // when
    final var stores = ServiceClusters.fetch(service.uri());

    // then
    assertThat(stores).containsOnly(entry("my cluster/1", WEST));
    assertThat(service.requests()).contains("/v1/credentials?cluster=my+cluster%2F1");
  }

  @Test
  void shouldFailWithTheStatusOfTheList() {
    // given
    service.respond("/v1/clusters", 500, "{\"error\":\"boom\"}");

    // when, then
    assertThatThrownBy(() -> ServiceClusters.fetch(service.uri()))
        .isInstanceOfSatisfying(
            ServiceResponseException.class,
            e -> {
              assertThat(e.status()).isEqualTo(500);
              assertThat(e.uri()).isEqualTo(URI.create(service.uri() + "/v1/clusters"));
            })
        .hasMessageContaining("Expected status 200 for GET " + service.uri() + "/v1/clusters")
        .hasMessageContaining("but got 500: {\"error\":\"boom\"}");
  }

  @Test
  void shouldFailWithTheStatusOfAClusterThatIsGoneFromTheService() {
    // given
    service.respond("/v1/clusters", 200, "{\"clusters\":{\"east\":{}}}");

    // when, then
    assertThatThrownBy(() -> ServiceClusters.fetch(service.uri()))
        .isInstanceOfSatisfying(
            ServiceResponseException.class, e -> assertThat(e.status()).isEqualTo(404))
        .hasMessageContaining("GET " + service.uri() + "/v1/credentials?cluster=east");
  }

  @Test
  void shouldFailIfTheServiceIsNotThere() throws Exception {
    // given
    final URI gone;
    try (final var stopped = StubService.start()) {
      gone = stopped.uri();
    }

    // when, then
    assertThatThrownBy(() -> ServiceClusters.fetch(gone)).isInstanceOf(IOException.class);
  }

  @ParameterizedTest
  @MethodSource("wrongLists")
  void shouldFailOnAListThatIsNotWhatTheServiceAnswers(final String body, final String message) {
    // given
    service.respond("/v1/clusters", 200, body);

    // when, then
    assertThatThrownBy(() -> ServiceClusters.fetch(service.uri()))
        .isInstanceOf(InvalidObjectJsonException.class)
        .hasMessageContaining(message);
  }

  static Stream<Arguments> wrongLists() {
    return Stream.of(
        Arguments.of("", "Expected clusters to be a JSON object of cluster ids, but got nothing"),
        Arguments.of("[]", "Expected clusters to be a JSON object of cluster ids, but got nothing"),
        Arguments.of(
            "{\"clusters\":[]}",
            "Expected clusters to be a JSON object of cluster ids, but got ARRAY"),
        Arguments.of("not json", " to be JSON, but got invalid JSON at line 1, column "));
  }

  @ParameterizedTest
  @MethodSource("wrongCredentials")
  void shouldFailOnCredentialsThatAreNotWhatTheServiceAnswers(
      final String body, final String message) {
    // given
    service.respond("/v1/clusters", 200, "{\"clusters\":{\"east\":{}}}");
    service.respond("/v1/credentials?cluster=east", 200, body);

    // when, then
    assertThatThrownBy(() -> ServiceClusters.fetch(service.uri()))
        .isInstanceOf(InvalidObjectJsonException.class)
        .hasMessageContaining(message);
  }

  static Stream<Arguments> wrongCredentials() {
    final var name = "Expected credentials[east]";
    return Stream.of(
        Arguments.of("[]", name + " to be a JSON object of store settings, but got ARRAY"),
        Arguments.of("{", " to be JSON, but got invalid JSON at line 1, column "),
        Arguments.of("{}", name + ".type to be filesystem or s3, but got nothing"),
        Arguments.of("{\"type\":5}", name + ".type to be text, but got NUMBER"),
        Arguments.of(
            "{\"type\":\"gcs\"}",
            name
                + " to be a valid store, but got one that isn't (Expected type to be filesystem or"
                + " s3, but got gcs)"),
        Arguments.of(
            "{\"type\":\"s3\"}", "(Expected bucket to be set for an S3 store, but got none)"),
        Arguments.of(
            "{\"type\":\"filesystem\"}",
            "(Expected path to be set for a filesystem store, but got none)"),
        Arguments.of("{\"type\":\"s3\",\"bucket\":5}", name + ".bucket to be text, but got NUMBER"),
        Arguments.of(
            "{\"type\":\"s3\",\"bucket\":\"b\",\"forcePathStyle\":\"yes\"}",
            name + ".forcePathStyle to be true or false, but got STRING"),
        Arguments.of(
            "{\"type\":\"s3\",\"bucket\":\"b\",\"endpoint\":\"http://a b\"}",
            name + ".endpoint to be a URI, but got 'http://a b'"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"type\":\"s3\",\"bucket\":5,\"secretAccessKey\":\"very-secret\"}",
        "{\"type\":\"s3\",\"secretAccessKey\":\"very-secret\"}",
        "{\"type\":\"gcs\",\"secretAccessKey\":\"very-secret\"}",
        "{\"secretAccessKey\":\"very-secret\"}",
        "{\"secretAccessKey\":\"very-secret\"} x}",
        "very-secret",
      })
  void shouldNotQuoteTheSecretsOfCredentialsThatAreWrong(final String body) {
    // given
    service.respond("/v1/clusters", 200, "{\"clusters\":{\"east\":{}}}");
    service.respond("/v1/credentials?cluster=east", 200, body);

    // when, then
    assertThatThrownBy(() -> ServiceClusters.fetch(service.uri()))
        .isInstanceOf(InvalidObjectJsonException.class)
        .hasMessageNotContaining("very-secret");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.eventplane.store.InvalidStoreConfigException;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ServiceSettingsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldDefaultToAFilesystemStoreAndPort8085() throws Exception {
    // given
    final var properties = Map.of("eventplane.store.path", "/data/event-plane");

    // when
    final var settings = ServiceSettings.from(properties::get);

    // then
    assertThat(settings.store().type()).isEqualTo(Type.FILESYSTEM);
    assertThat(settings.store().path()).isEqualTo(Path.of("/data/event-plane"));
    assertThat(settings.service().address().getHostString()).isEqualTo("0.0.0.0");
    assertThat(settings.service().address().getPort()).isEqualTo(8085);
    assertThat(settings.service().peers()).isEmpty();
    assertThat(settings.service().keepAlive()).isEqualTo(Duration.ofSeconds(15));
    assertThat(settings.service().clusters()).isEmpty();
    assertThat(MAPPER.readTree(settings.service().credentialsJson()))
        .isEqualTo(
            MAPPER.readTree(
                "{\"type\":\"filesystem\",\"path\":\"/data/event-plane\",\"forcePathStyle\":false}"));
  }

  @Test
  void shouldReadTheStoresOfTheClusters() {
    // given
    final var properties =
        Map.of(
            "eventplane.store.path",
            "/data/home",
            "eventplane.service.clusters",
            """
            {"east":{"type":"S3","bucket":"east-bucket","basePath":"plane","region":"us-east-1",
                     "endpoint":"http://minio:9000","accessKeyId":"key",
                     "secretAccessKey":"secret","forcePathStyle":true,"unknown":1},
             "west":{"type":"filesystem","path":"/data/west"}}""");

    // when
    final var clusters = ServiceSettings.from(properties::get).service().clusters();

    // then
    assertThat(clusters)
        .containsOnly(
            Map.entry(
                "east",
                new StoreConfig(
                    Type.S3,
                    null,
                    "east-bucket",
                    "plane",
                    "us-east-1",
                    URI.create("http://minio:9000"),
                    "key",
                    "secret",
                    true)),
            Map.entry("west", StoreConfig.filesystem(Path.of("/data/west"))));
  }

  @Test
  void shouldReadTheSettingsOfAnS3StoreAndTheService() throws Exception {
    // given
    final var properties =
        Map.ofEntries(
            Map.entry("eventplane.store.type", "S3"),
            Map.entry("eventplane.store.bucket", "my-bucket"),
            Map.entry("eventplane.store.basePath", "event-plane"),
            Map.entry("eventplane.store.region", "us-east-1"),
            Map.entry("eventplane.store.endpoint", "http://minio:9000"),
            Map.entry("eventplane.store.accessKeyId", "key"),
            Map.entry("eventplane.store.secretAccessKey", "secret"),
            Map.entry("eventplane.store.forcePathStyle", "true"),
            Map.entry("eventplane.service.host", "127.0.0.1"),
            Map.entry("eventplane.service.port", "9000"),
            Map.entry("eventplane.service.peers", "http://service-2:8085, https://service-3"),
            Map.entry("eventplane.service.keepAlive", "PT5S"));

    // when
    final var settings = ServiceSettings.from(properties::get);

    // then
    final var store = settings.store();
    assertThat(store.type()).isEqualTo(Type.S3);
    assertThat(store.bucket()).isEqualTo("my-bucket");
    assertThat(store.basePath()).isEqualTo("event-plane");
    assertThat(store.region()).isEqualTo("us-east-1");
    assertThat(store.endpoint()).isEqualTo(URI.create("http://minio:9000"));
    assertThat(store.accessKeyId()).isEqualTo("key");
    assertThat(store.secretAccessKey()).isEqualTo("secret");
    assertThat(store.forcePathStyle()).isTrue();
    final var service = settings.service();
    assertThat(service.address().getHostString()).isEqualTo("127.0.0.1");
    assertThat(service.address().getPort()).isEqualTo(9000);
    assertThat(service.peers())
        .containsExactly(URI.create("http://service-2:8085"), URI.create("https://service-3"));
    assertThat(service.keepAlive()).isEqualTo(Duration.ofSeconds(5));
    assertThat(MAPPER.readTree(service.credentialsJson()))
        .isEqualTo(
            MAPPER.readTree(
                """
                {"type":"s3","bucket":"my-bucket","basePath":"event-plane","region":"us-east-1",
                 "endpoint":"http://minio:9000","accessKeyId":"key","secretAccessKey":"secret",
                 "forcePathStyle":true}"""));
  }

  @Test
  void shouldAnswerTheCredentialsThatAreSet() {
    // given
    final var properties =
        Map.of(
            "eventplane.store.path", "/data",
            "eventplane.service.credentials", "{\"token\":\"t\"}");

    // when
    final var settings = ServiceSettings.from(properties::get);

    // then
    assertThat(settings.service().credentialsJson()).isEqualTo("{\"token\":\"t\"}");
  }

  @Test
  void shouldTreatABlankValueAsUnset() {
    // given
    final var properties =
        Map.of(
            "eventplane.store.type", " ",
            "eventplane.store.path", "/data",
            "eventplane.service.port", "",
            "eventplane.service.peers", "  ",
            "eventplane.service.clusters", " ");

    // when
    final var settings = ServiceSettings.from(properties::get);

    // then
    assertThat(settings.store().type()).isEqualTo(Type.FILESYSTEM);
    assertThat(settings.service().address().getPort()).isEqualTo(8085);
    assertThat(settings.service().peers()).isEmpty();
    assertThat(settings.service().clusters()).isEmpty();
  }

  @Test
  void shouldNotShowTheSecretsOfTheSettings() {
    // given
    final var properties =
        Map.of(
            "eventplane.store.type", "s3",
            "eventplane.store.bucket", "b",
            "eventplane.store.accessKeyId", "key",
            "eventplane.store.secretAccessKey", "very-secret");

    // when
    final var text = ServiceSettings.from(properties::get).toString();

    // then
    assertThat(text).doesNotContain("very-secret").contains("bucket=b");
  }

  @ParameterizedTest
  @CsvSource({
    "eventplane.service.port,http,to be a port from 0 to 65535",
    "eventplane.service.port,65536,to be a port from 0 to 65535",
    "eventplane.service.port,-1,to be a port from 0 to 65535",
    "eventplane.service.keepAlive,15,to be an ISO-8601 duration",
    "eventplane.service.keepAlive,PT0S,to be longer than zero",
    "eventplane.service.peers,ftp://peer,to be an http or https URL with a host",
    "eventplane.service.peers,peer:8085,to be an http or https URL with a host",
    "eventplane.service.credentials,[],to be a JSON object",
    "eventplane.store.forcePathStyle,yes,to be true or false",
  })
  void shouldRejectASettingThatIsWrong(
      final String name, final String value, final String expected) {
    // given
    final var properties = Map.of("eventplane.store.path", "/data", name, value);

    // when, then
    assertThatThrownBy(() -> ServiceSettings.from(properties::get))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessageContaining(expected)
        .hasMessageContaining("but got");
  }

  @ParameterizedTest
  @MethodSource("wrongClusters")
  void shouldRejectClustersThatAreWrong(final String json, final String message) {
    // given
    final var properties =
        Map.of("eventplane.store.path", "/data", "eventplane.service.clusters", json);

    // when, then
    assertThatThrownBy(() -> ServiceSettings.from(properties::get))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessageContaining(message);
  }

  static Stream<Arguments> wrongClusters() {
    final var name = "Expected eventplane.service.clusters";
    return Stream.of(
        Arguments.of("[]", name + " to be a JSON object of cluster ids and stores, but got ARRAY"),
        Arguments.of("{\"a\":", name + " to be JSON, but got invalid JSON at line 1"),
        Arguments.of(
            "{\"a\":[]}", name + ".a to be a JSON object of store settings, but got ARRAY"),
        Arguments.of("{\"a\":{}}", name + ".a.type to be filesystem or s3, but got none"),
        Arguments.of("{\"a\":{\"type\":5}}", name + ".a.type to be text, but got NUMBER"),
        Arguments.of(
            "{\"a\":{\"type\":\"gcs\"}}",
            name + ".a to be a valid store, but got one that isn't (Expected type to be"),
        Arguments.of(
            "{\"a\":{\"type\":\"s3\"}}",
            "(Expected bucket to be set for an S3 store, but got none)"),
        Arguments.of(
            "{\"a\":{\"type\":\"filesystem\"}}",
            "(Expected path to be set for a filesystem store, but got none)"),
        Arguments.of(
            "{\"a\":{\"type\":\"s3\",\"bucket\":5}}",
            name + ".a.bucket to be text, but got NUMBER"),
        Arguments.of(
            "{\"a\":{\"type\":\"s3\",\"bucket\":\"b\",\"forcePathStyle\":\"yes\"}}",
            name + ".a.forcePathStyle to be true or false, but got STRING"),
        Arguments.of(
            "{\"a\":{\"type\":\"s3\",\"bucket\":\"b\",\"endpoint\":\"http://a b\"}}",
            name + ".a.endpoint to be a URI, but got 'http://a b'"),
        Arguments.of(
            "{\"\":{\"type\":\"s3\",\"bucket\":\"b\"}}",
            "Expected a cluster id to be set and not start with '_', but got ''"),
        Arguments.of(
            "{\"_groups\":{\"type\":\"s3\",\"bucket\":\"b\"}}",
            "Expected a cluster id to be set and not start with '_', but got '_groups'"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"a\":{\"type\":\"s3\",\"bucket\":5,\"secretAccessKey\":\"very-secret\"}}",
        "{\"a\":{\"type\":\"s3\",\"secretAccessKey\":\"very-secret\"}}",
        "{\"a\":{\"type\":\"gcs\",\"secretAccessKey\":\"very-secret\"}}",
        "{\"a\":{\"secretAccessKey\":\"very-secret\"}}",
        "{\"a\":{\"secretAccessKey\":\"very-secret\"} x}",
        "very-secret",
      })
  void shouldNotQuoteTheSecretsOfClustersThatAreWrong(final String json) {
    // given
    final var properties =
        Map.of("eventplane.store.path", "/data", "eventplane.service.clusters", json);

    // when, then
    assertThatThrownBy(() -> ServiceSettings.from(properties::get))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessageNotContaining("very-secret");
  }

  @Test
  void shouldRejectAFilesystemStoreWithoutAPath() {
    // when, then
    assertThatThrownBy(() -> ServiceSettings.from(name -> null))
        .isInstanceOf(InvalidStoreConfigException.class)
        .hasMessage("Expected path to be set for a filesystem store, but got none");
  }

  @Test
  void shouldRejectAnUnknownStoreType() {
    // given
    final var properties = Map.of("eventplane.store.type", "gcs");

    // when, then
    assertThatThrownBy(() -> ServiceSettings.from(properties::get))
        .isInstanceOf(InvalidStoreConfigException.class)
        .hasMessage("Expected type to be filesystem or s3, but got gcs");
  }

  @ParameterizedTest
  @CsvSource({
    "eventplane.store.type,EVENTPLANE_STORE_TYPE",
    "eventplane.store.basePath,EVENTPLANE_STORE_BASE_PATH",
    "eventplane.store.accessKeyId,EVENTPLANE_STORE_ACCESS_KEY_ID",
    "eventplane.store.forcePathStyle,EVENTPLANE_STORE_FORCE_PATH_STYLE",
    "eventplane.service.keepAlive,EVENTPLANE_SERVICE_KEEP_ALIVE",
    "eventplane.service.peers,EVENTPLANE_SERVICE_PEERS",
    "eventplane.service.clusters,EVENTPLANE_SERVICE_CLUSTERS",
  })
  void shouldNameTheEnvironmentVariableOfAProperty(final String property, final String variable) {
    // when, then
    assertThat(ServiceSettings.environmentVariable(property)).isEqualTo(variable);
  }
}

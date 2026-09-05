/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationJsonSerializer;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code /actuator/cluster/dump} against a running broker. The serialization itself is
 * covered by the round-trip property in {@code ClusterConfigurationJsonSerializerPropertyTest};
 * what only a real broker can show is that both encodings survive the management context's MVC
 * layer - the JSON is handed over pre-serialized, and the protobuf needs a byte[] converter and a
 * content type no other endpoint here uses - and which encoding content negotiation actually
 * settles on.
 */
@ZeebeIntegration
final class ClusterConfigurationDumpIT {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  static TestStandaloneBroker broker;

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker =
        new TestStandaloneBroker()
            .withUnifiedConfig(cfg -> cfg.getCluster().setClusterId("dumped-cluster"));
  }

  @Test
  void shouldDumpTheConfigurationWhenJsonIsAccepted() throws IOException, InterruptedException {
    // when
    final var response = get("application/json", BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(contentTypeOf(response)).contains("application/json");
    // the model's own field names, unlike the mapped /actuator/cluster response where these
    // same values appear as a flat list of brokers
    assertThat(response.body())
        .contains("\"globalConfiguration\"")
        .contains("\"partitionGroups\"")
        .contains("dumped-cluster");
  }

  @Test
  void shouldDumpDecodableProtobufWhenProtobufIsAccepted()
      throws IOException, InterruptedException {
    // when
    final var response = get("application/x-protobuf", BodyHandlers.ofByteArray());

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(contentTypeOf(response)).contains("application/x-protobuf");

    final var body = response.body();
    final var decoded = new ProtoBufSerializer().decodeCurrentClusterConfiguration(body);
    assertThat(decoded.globalConfiguration().clusterId()).contains("dumped-cluster");
    assertThat(decoded.getMembers()).hasSize(1);
    assertThat(decoded.partitionGroups()).containsOnlyKeys("default");
  }

  @Test
  void shouldDescribeTheSameConfigurationInBothEncodings()
      throws IOException, InterruptedException {
    // when
    final var asJson = get("application/json", BodyHandlers.ofString());
    final var asProtobuf = get("application/x-protobuf", BodyHandlers.ofByteArray());

    // then — the two encodings are two views of one fetched configuration, so decoding the
    // binary one and rendering it has to reproduce the JSON one
    final var fromProtobuf =
        new ProtoBufSerializer().decodeCurrentClusterConfiguration(asProtobuf.body());
    assertThat(asJson.body()).isEqualTo(ClusterConfigurationJsonSerializer.toJson(fromProtobuf));
  }

  @Test
  void shouldDumpJsonWhenNothingIsAccepted() throws IOException, InterruptedException {
    // when — what a bare `curl` sends, so this pins which encoding content negotiation picks when
    // the client expresses no preference. Binary would be a poor answer for an interactive request.
    final var response = get(null, BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(contentTypeOf(response)).contains("application/json");
  }

  @Test
  void shouldRejectAnUnsupportedEncoding() throws IOException, InterruptedException {
    // when
    final var response = get("application/xml", BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(406);
  }

  private String contentTypeOf(final HttpResponse<?> response) {
    return response.headers().firstValue("content-type").orElse("");
  }

  private <T> HttpResponse<T> get(final String accept, final BodyHandler<T> bodyHandler)
      throws IOException, InterruptedException {
    final var request = HttpRequest.newBuilder().uri(broker.actuatorUri("cluster", "dump"));
    if (accept != null) {
      request.header("Accept", accept);
    }
    try (final var client = HttpClient.newHttpClient()) {
      return client.send(request.build(), bodyHandler);
    }
  }
}

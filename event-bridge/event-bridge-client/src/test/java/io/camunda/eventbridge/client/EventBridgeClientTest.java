/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@WireMockTest
class EventBridgeClientTest {

  private EventBridgeClient client;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo wmRuntimeInfo) {
    client = EventBridgeClient.create("http://localhost:" + wmRuntimeInfo.getHttpPort());
  }

  @Nested
  class PublishBatch {

    @Test
    void shouldReturnAssignedPositions() throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"positions\":[1001,1002]}")));

      final byte[] payload1 = {1, 2, 3};
      final byte[] payload2 = {4, 5, 6};

      // when
      final List<Long> positions = client.publishBatch(0, List.of(payload1, payload2)).get();

      // then
      assertThat(positions).containsExactly(1001L, 1002L);
    }

    @Test
    void shouldThrowEventBridgeExceptionOnPartitionNotFound() {
      // given
      stubFor(
          post(urlEqualTo("/v1/events/99"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"error\":\"PARTITION_NOT_FOUND\",\"message\":\"No such partition\"}")));

      // when / then
      assertThatThrownBy(() -> client.publishBatch(99, List.of(new byte[] {1})).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(EventBridgeException.class)
          .hasMessageContaining("400");
    }

    @Test
    void shouldThrowEventBridgeExceptionOnLeaderUnavailable() {
      // given
      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .willReturn(
                  aResponse()
                      .withStatus(503)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\":\"LEADER_UNAVAILABLE\",\"message\":\"No leader\"}")));

      // when / then
      assertThatThrownBy(() -> client.publishBatch(0, List.of(new byte[] {1})).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(EventBridgeException.class)
          .hasMessageContaining("503");
    }

    @Test
    void shouldEncodePayloadsAsBase64InRequest() throws Exception {
      // given
      final byte[] payload = {0x01, 0x02, 0x03};
      final String expectedBase64 = Base64.getEncoder().encodeToString(payload);

      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .withRequestBody(
                  com.github.tomakehurst.wiremock.client.WireMock.containing(expectedBase64))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"positions\":[42]}")));

      // when
      final List<Long> positions = client.publishBatch(0, List.of(payload)).get();

      // then
      assertThat(positions).containsExactly(42L);
    }

    @Test
    void shouldHandleSingleEventBatch() throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/events/2"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"positions\":[500]}")));

      // when
      final List<Long> positions = client.publishBatch(2, List.of(new byte[] {9, 8, 7})).get();

      // then
      assertThat(positions).containsExactly(500L);
    }

    @Test
    void shouldStripTrailingSlashFromGatewayUrl(final WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      // given — client created with a trailing slash in the base URL
      final var trailingSlashClient =
          EventBridgeClient.create("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/");
      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"positions\":[1]}")));

      // when — request must go to /v1/events/0, not //v1/events/0
      final List<Long> positions =
          trailingSlashClient.publishBatch(0, List.of(new byte[] {1})).get();

      // then
      assertThat(positions).containsExactly(1L);
    }

    @Test
    void shouldSendApplicationJsonContentTypeHeader(final WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .withHeader("Content-Type", WireMock.containing("application/json"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"positions\":[10]}")));

      // when
      final List<Long> positions = client.publishBatch(0, List.of(new byte[] {1})).get();

      // then — WireMock only matches the stub if Content-Type is present; positions returned means
      // header was correct
      assertThat(positions).containsExactly(10L);
    }
  }

  @Nested
  class Subscribe {

    @Test
    void shouldReturnConsumerHandleWithAssignedPartitionsAndGeneration() throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/my-group/consumer-1/subscribe"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"assignedPartitions\":[0,1],\"generation\":3}")));

      // when
      final Consumer consumer = client.subscribe("my-group", "consumer-1").get();

      // then
      assertThat(consumer.getGroupId()).isEqualTo("my-group");
      assertThat(consumer.getConsumerId()).isEqualTo("consumer-1");
      assertThat(consumer.getAssignedPartitions()).containsExactly(0, 1);
      assertThat(consumer.getGeneration()).isEqualTo(3L);
    }

    @Test
    void shouldSortAssignedPartitionsAscending() throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/grp/c1/subscribe"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"assignedPartitions\":[3,1,0,2],\"generation\":1}")));

      // when
      final Consumer consumer = client.subscribe("grp", "c1").get();

      // then
      assertThat(consumer.getAssignedPartitions()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldThrowCoordinatorUnavailableExceptionOn503() {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/grp/c1/subscribe"))
              .willReturn(
                  aResponse()
                      .withStatus(503)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"ERROR\",\"error\":\"COORDINATOR_UNAVAILABLE\",\"message\":\"Coordinator down\"}")));

      // when / then
      assertThatThrownBy(() -> client.subscribe("grp", "c1").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorUnavailableException.class);
    }

    @Test
    void shouldThrowEventBridgeExceptionOnOtherErrors() {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/grp/c1/subscribe"))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\":\"INTERNAL_ERROR\"}")));

      // when / then
      assertThatThrownBy(() -> client.subscribe("grp", "c1").get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(EventBridgeException.class);
    }

    @Test
    void shouldReturnConsumerWithNoAssignedPartitions() throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/grp/c1/subscribe"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"status\":\"OK\",\"assignedPartitions\":[],\"generation\":1}")));

      // when
      final Consumer consumer = client.subscribe("grp", "c1").get();

      // then
      assertThat(consumer.getAssignedPartitions()).isEmpty();
      assertThat(consumer.getGeneration()).isEqualTo(1L);
    }
  }

  @Nested
  class GetLatestPosition {

    @Test
    void shouldReturnLatestPosition() {
      // given
      stubFor(
          get(urlEqualTo("/v1/partitions/0/latest-position"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"position\":1042}")));

      // when
      final long position = client.getLatestPosition(0);

      // then
      assertThat(position).isEqualTo(1042L);
    }

    @Test
    void shouldReturnZeroForEmptyPartition() {
      // given
      stubFor(
          get(urlEqualTo("/v1/partitions/1/latest-position"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"position\":0}")));

      // when
      final long position = client.getLatestPosition(1);

      // then
      assertThat(position).isZero();
    }

    @Test
    void shouldThrowOnPartitionNotFound() {
      // given
      stubFor(
          get(urlEqualTo("/v1/partitions/99/latest-position"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"error\":\"PARTITION_NOT_FOUND\",\"message\":\"No such partition\"}")));

      // when / then
      assertThatThrownBy(() -> client.getLatestPosition(99))
          .isInstanceOf(EventBridgeException.class)
          .hasMessageContaining("400");
    }

    @Test
    void shouldThrowOnServiceUnavailable() {
      // given
      stubFor(
          get(urlEqualTo("/v1/partitions/0/latest-position"))
              .willReturn(
                  aResponse().withStatus(503).withBody("{\"error\":\"LEADER_UNAVAILABLE\"}")));

      // when / then
      assertThatThrownBy(() -> client.getLatestPosition(0))
          .isInstanceOf(EventBridgeException.class)
          .hasMessageContaining("503");
    }
  }
}

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
import io.camunda.eventbridge.core.EventData;
import io.camunda.eventbridge.core.EventDataBatch;
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
                      .withBody("{\"logPositions\":[1001,1002]}")));

      final byte[] payload1 = {1, 2, 3};
      final byte[] payload2 = {4, 5, 6};
      final EventDataBatch batch = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      batch.tryAdd(new EventData(payload1));
      batch.tryAdd(new EventData(payload2));

      // when
      final List<Long> positions = client.publishBatch(0, batch).get();

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
      final EventDataBatch batch99 = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      batch99.tryAdd(new EventData(new byte[] {1}));
      assertThatThrownBy(() -> client.publishBatch(99, batch99).get())
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
      final EventDataBatch batch503 = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      batch503.tryAdd(new EventData(new byte[] {1}));
      assertThatThrownBy(() -> client.publishBatch(0, batch503).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(EventBridgeException.class)
          .hasMessageContaining("503");
    }

    @Test
    void shouldSendBatchAsBinaryOctetStream() throws Exception {
      // given
      final byte[] payload = {0x01, 0x02, 0x03};
      final EventDataBatch batch = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      batch.tryAdd(new EventData(payload));

      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"logPositions\":[42]}")));

      // when
      final List<Long> positions = client.publishBatch(0, batch).get();

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
                      .withBody("{\"logPositions\":[500]}")));

      final EventDataBatch batch = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      batch.tryAdd(new EventData(new byte[] {9, 8, 7}));

      // when
      final List<Long> positions = client.publishBatch(2, batch).get();

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
                      .withBody("{\"logPositions\":[1]}")));

      final EventDataBatch trailingBatch = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      trailingBatch.tryAdd(new EventData(new byte[] {1}));

      // when — request must go to /v1/events/0, not //v1/events/0
      final List<Long> positions = trailingSlashClient.publishBatch(0, trailingBatch).get();

      // then
      assertThat(positions).containsExactly(1L);
    }

    @Test
    void shouldSendApplicationOctetStreamContentTypeHeader(final WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      // given
      stubFor(
          post(urlEqualTo("/v1/events/0"))
              .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"logPositions\":[10]}")));

      final EventDataBatch batch = EventDataBatch.create(10_485_760, 1_048_576, 1000);
      batch.tryAdd(new EventData(new byte[] {1}));

      // when
      final List<Long> positions = client.publishBatch(0, batch).get();

      // then — WireMock only matches the stub if Content-Type is present; positions returned means
      // header was correct
      assertThat(positions).containsExactly(10L);
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

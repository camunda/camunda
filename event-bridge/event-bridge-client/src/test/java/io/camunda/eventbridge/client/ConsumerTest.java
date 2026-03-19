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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@WireMockTest
class ConsumerTest {

  private static final String GROUP_ID = "test-group";
  private static final String CONSUMER_ID = "consumer-1";
  private static final long GENERATION = 1L;

  private EventBridgeClient client;
  private Consumer consumer;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo wmRuntimeInfo) {
    client = EventBridgeClient.create("http://localhost:" + wmRuntimeInfo.getHttpPort());
    consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), GENERATION, client);
  }

  @Nested
  class Poll {

    @Test
    void shouldReturnEmptyListWhenNoEventsAvailable() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":100,\"generation\":1}")));

      // when
      final List<Event> events = consumer.poll(10, Duration.ZERO);

      // then
      assertThat(events).isEmpty();
    }

    @Test
    void shouldReturnEventsWithDecodedPayloads() {
      // given
      final byte[] payload = {0x41, 0x42, 0x43};
      final String base64Payload = Base64.getEncoder().encodeToString(payload);

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":["
                              + "{\"position\":1001,\"payload\":\""
                              + base64Payload
                              + "\"}"
                              + "],\"nextPosition\":1002,\"generation\":1}")));

      // when
      final List<Event> events = consumer.poll(10, Duration.ZERO);

      // then
      assertThat(events).hasSize(1);
      final Event event = events.get(0);
      assertThat(event.position()).isEqualTo(1001L);
      assertThat(event.partitionId()).isEqualTo(0);
      assertThat(event.payload()).isEqualTo(payload);
    }

    @Test
    void shouldReturnEventsFromMultiplePartitions() {
      // given
      final Consumer multiPartitionConsumer =
          new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), GENERATION, client);

      final byte[] payload0 = {0x01};
      final byte[] payload1 = {0x02};

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":["
                              + "{\"position\":100,\"payload\":\""
                              + Base64.getEncoder().encodeToString(payload0)
                              + "\"}"
                              + "],\"nextPosition\":101,\"generation\":1}")));

      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":["
                              + "{\"position\":200,\"payload\":\""
                              + Base64.getEncoder().encodeToString(payload1)
                              + "\"}"
                              + "],\"nextPosition\":201,\"generation\":1}")));

      // when
      final List<Event> events = multiPartitionConsumer.poll(10, Duration.ZERO);

      // then
      assertThat(events).hasSize(2);
      assertThat(events.get(0).partitionId()).isEqualTo(0);
      assertThat(events.get(0).position()).isEqualTo(100L);
      assertThat(events.get(1).partitionId()).isEqualTo(1);
      assertThat(events.get(1).position()).isEqualTo(200L);
    }

    @Test
    void shouldIteratePartitionsInAscendingOrder() {
      // given — consumer assigned to partitions 2, 0, 1 (deliberately out of order)
      final Consumer unsortedConsumer =
          new Consumer(GROUP_ID, CONSUMER_ID, List.of(2, 0, 1), GENERATION, client);

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":10,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":11,\"generation\":1}")));
      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":20,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":21,\"generation\":1}")));
      stubFor(
          get(urlMatching("/v1/events/2/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":30,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":31,\"generation\":1}")));

      // when
      final List<Event> events = unsortedConsumer.poll(10, Duration.ZERO);

      // then — events arrive in partition ID order (0, 1, 2)
      assertThat(events).hasSize(3);
      assertThat(events.get(0).partitionId()).isEqualTo(0);
      assertThat(events.get(1).partitionId()).isEqualTo(1);
      assertThat(events.get(2).partitionId()).isEqualTo(2);
    }

    @Test
    void shouldTrackNextPositionBetweenPolls() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":100,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":101,\"generation\":1}")));

      // when — first poll
      consumer.poll(10, Duration.ZERO);

      // then — second poll uses nextPosition=101 from previous response
      verify(
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("fromPosition", WireMock.equalTo("-1")));

      // second poll
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":101,\"generation\":1}")));

      consumer.poll(10, Duration.ZERO);

      verify(
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("fromPosition", WireMock.equalTo("101")));
    }

    @Test
    void shouldStartPollWithNegativeOneInitially() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":0,\"generation\":1}")));

      // when
      consumer.poll(10, Duration.ZERO);

      // then — initial fromPosition is -1
      verify(
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("fromPosition", WireMock.equalTo("-1")));
    }

    @Test
    void shouldPassGenerationInPollRequest() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":0,\"generation\":1}")));

      // when
      consumer.poll(10, Duration.ZERO);

      // then
      verify(
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("generation", WireMock.equalTo("1")));
    }

    @Test
    void shouldPassServerWaitMsFromTimeout() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":0,\"generation\":1}")));

      // when
      consumer.poll(5, Duration.ofMillis(500));

      // then
      verify(
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("serverWaitMs", WireMock.equalTo("500"))
              .withQueryParam("maxRecords", WireMock.equalTo("5")));
    }

    @Test
    void shouldThrowRebalanceInProgressExceptionAndUpdateAssignment() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"REBALANCE_IN_PROGRESS\","
                              + "\"assignedPartitions\":[0,1],"
                              + "\"events\":[],"
                              + "\"generation\":2}")));

      // when / then
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(RebalanceInProgressException.class)
          .satisfies(
              e -> {
                final var rebalance = (RebalanceInProgressException) e;
                assertThat(rebalance.getNewAssignment()).containsExactly(0, 1);
              });
    }

    @Test
    void shouldUpdateGenerationOnRebalanceInProgress() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"REBALANCE_IN_PROGRESS\","
                              + "\"assignedPartitions\":[0,1],"
                              + "\"events\":[],"
                              + "\"generation\":5}")));

      // when
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(RebalanceInProgressException.class);

      // then — generation updated to 5
      assertThat(consumer.getGeneration()).isEqualTo(5L);
      assertThat(consumer.getAssignedPartitions()).containsExactly(0, 1);
    }

    @Test
    void shouldDiscardAllEventsOnRebalanceInProgress() {
      // given — two partitions; partition 0 has an event, partition 1 triggers rebalance
      final Consumer multiPartitionConsumer =
          new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), GENERATION, client);

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":10,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":11,\"generation\":1}")));

      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"REBALANCE_IN_PROGRESS\","
                              + "\"assignedPartitions\":[0],"
                              + "\"events\":[],"
                              + "\"generation\":2}")));

      // when / then — exception thrown; partial results discarded
      assertThatThrownBy(() -> multiPartitionConsumer.poll(10, Duration.ZERO))
          .isInstanceOf(RebalanceInProgressException.class)
          .satisfies(
              e ->
                  assertThat(((RebalanceInProgressException) e).getNewAssignment())
                      .containsExactly(0));
    }

    @Test
    void shouldSendGroupIdAndConsumerIdInPollRequest() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":0,\"generation\":1}")));

      // when
      consumer.poll(10, Duration.ZERO);

      // then
      verify(
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("groupId", WireMock.equalTo(GROUP_ID))
              .withQueryParam("consumerId", WireMock.equalTo(CONSUMER_ID)));
    }

    @Test
    void shouldHandleMultipleEventsPerPartition() {
      // given
      final byte[] p1 = {0x01};
      final byte[] p2 = {0x02};
      final byte[] p3 = {0x03};

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":["
                              + "{\"position\":10,\"payload\":\""
                              + Base64.getEncoder().encodeToString(p1)
                              + "\"},"
                              + "{\"position\":11,\"payload\":\""
                              + Base64.getEncoder().encodeToString(p2)
                              + "\"},"
                              + "{\"position\":12,\"payload\":\""
                              + Base64.getEncoder().encodeToString(p3)
                              + "\"}"
                              + "],\"nextPosition\":13,\"generation\":1}")));

      // when
      final List<Event> events = consumer.poll(10, Duration.ZERO);

      // then
      assertThat(events).hasSize(3);
      assertThat(events.get(0).position()).isEqualTo(10L);
      assertThat(events.get(0).payload()).isEqualTo(p1);
      assertThat(events.get(1).position()).isEqualTo(11L);
      assertThat(events.get(1).payload()).isEqualTo(p2);
      assertThat(events.get(2).position()).isEqualTo(12L);
      assertThat(events.get(2).payload()).isEqualTo(p3);
    }

    @Test
    void shouldThrowEventBridgeExceptionOn409StaleGeneration() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(409)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"error\":\"STALE_GENERATION\",\"currentGeneration\":5}")));

      // when / then
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(EventBridgeException.class)
          .hasMessageContaining("Stale generation");
    }

    @Test
    void shouldReturnEmptyListImmediatelyWhenNoPartitionsAssigned() {
      // given — consumer with no assigned partitions; no HTTP stubs needed
      final Consumer emptyConsumer =
          new Consumer(GROUP_ID, CONSUMER_ID, List.of(), GENERATION, client);

      // when
      final List<Event> events = emptyConsumer.poll(10, Duration.ZERO);

      // then — empty list returned; no HTTP calls made
      assertThat(events).isEmpty();
    }

    @Test
    void shouldNotAdvanceNextPositionWhenResponseOmitsNextPosition() {
      // given — response has no "nextPosition" field
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"status\":\"OK\",\"events\":[],\"generation\":1}")));

      // when — first poll (fromPosition=-1)
      consumer.poll(10, Duration.ZERO);

      // then — second poll still uses -1 (position was not advanced)
      consumer.poll(10, Duration.ZERO);

      verify(
          2,
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("fromPosition", WireMock.equalTo("-1")));
    }

    @Test
    void shouldThrowEventBridgeExceptionOnPollError() {
      // given
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"ERROR\",\"error\":\"CONSUMER_NOT_REGISTERED\","
                              + "\"message\":\"Not registered\"}")));

      // when / then
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(EventBridgeException.class)
          .hasMessageContaining("400");
    }

    @Test
    void shouldThrowConsumerClosedExceptionAfterClose() {
      // given
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(ConsumerClosedException.class);
    }

    @Test
    void shouldNotAdvanceNextPositionForPartitionsPolleddBeforeRebalance() {
      // given — partition 0 is polled first (successfully), partition 1 triggers rebalance.
      // Per spec: "nextPosition is only advanced after events are successfully returned to
      // the caller." Events from partition 0 are discarded, so its position must not advance.
      final Consumer multiPartitionConsumer =
          new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), GENERATION, client);

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":10,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":11,\"generation\":1}")));
      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"REBALANCE_IN_PROGRESS\","
                              + "\"assignedPartitions\":[0,1],"
                              + "\"events\":[],"
                              + "\"generation\":2}")));

      // when — first poll throws due to rebalance
      assertThatThrownBy(() -> multiPartitionConsumer.poll(10, Duration.ZERO))
          .isInstanceOf(RebalanceInProgressException.class);

      // then — next poll for partition 0 must still start from -1 (position not advanced)
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":11,\"generation\":2}")));
      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":0,\"generation\":2}")));

      multiPartitionConsumer.poll(10, Duration.ZERO);

      verify(
          2,
          getRequestedFor(urlMatching("/v1/events/0/poll.*"))
              .withQueryParam("fromPosition", WireMock.equalTo("-1")));
    }
  }

  @Nested
  class CommitOffset {

    @Test
    void shouldCommitOffsetSuccessfully() {
      // given
      stubFor(post(urlMatching("/v1/events/0/commit.*")).willReturn(aResponse().withStatus(204)));

      // when / then — completes without exception
      consumer.commitOffset(0, 100L).join();
    }

    @Test
    void shouldAlsoAccept200OnCommit() {
      // given
      stubFor(
          post(urlMatching("/v1/events/0/commit.*"))
              .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"OK\"}")));

      // when / then — completes without exception
      consumer.commitOffset(0, 100L).join();
    }

    @Test
    void shouldSendGroupIdConsumerIdAndPositionAsQueryParams() {
      // given
      stubFor(post(urlMatching("/v1/events/0/commit.*")).willReturn(aResponse().withStatus(204)));

      // when
      consumer.commitOffset(0, 42L).join();

      // then
      verify(
          postRequestedFor(urlMatching("/v1/events/0/commit.*"))
              .withQueryParam("groupId", WireMock.equalTo(GROUP_ID))
              .withQueryParam("consumerId", WireMock.equalTo(CONSUMER_ID))
              .withQueryParam("position", WireMock.equalTo("42")));
    }

    @Test
    void shouldSendCurrentGenerationInCommitRequest() {
      // given — consumer has generation 1 from construction
      stubFor(post(urlMatching("/v1/events/0/commit.*")).willReturn(aResponse().withStatus(204)));

      // when
      consumer.commitOffset(0, 42L).join();

      // then — generation query param must be present so the broker can reject stale commits
      verify(
          postRequestedFor(urlMatching("/v1/events/0/commit.*"))
              .withQueryParam("generation", WireMock.equalTo("1")));
    }

    @Test
    void shouldThrowEventBridgeExceptionOnCommitError() {
      // given
      stubFor(
          post(urlMatching("/v1/events/0/commit.*"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withBody("{\"status\":\"ERROR\",\"error\":\"CONSUMER_NOT_REGISTERED\"}")));

      // when / then — join() wraps in CompletionException
      assertThatThrownBy(() -> consumer.commitOffset(0, 100L).join())
          .hasCauseInstanceOf(EventBridgeException.class)
          .cause()
          .hasMessageContaining("400");
    }

    @Test
    void shouldThrowEventBridgeExceptionOn409StaleGeneration() {
      // given
      stubFor(
          post(urlMatching("/v1/events/0/commit.*"))
              .willReturn(
                  aResponse()
                      .withStatus(409)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\":\"STALE_GENERATION\",\"currentGeneration\":3}")));

      // when / then
      assertThatThrownBy(() -> consumer.commitOffset(0, 50L).join())
          .hasCauseInstanceOf(EventBridgeException.class)
          .cause()
          .hasMessageContaining("409");
    }

    @Test
    void shouldThrowEventBridgeExceptionOn403PartitionNotAssigned() {
      // given
      stubFor(
          post(urlMatching("/v1/events/0/commit.*"))
              .willReturn(
                  aResponse()
                      .withStatus(403)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\":\"PARTITION_NOT_ASSIGNED\"}")));

      // when / then
      assertThatThrownBy(() -> consumer.commitOffset(0, 50L).join())
          .hasCauseInstanceOf(EventBridgeException.class)
          .cause()
          .hasMessageContaining("403");
    }

    @Test
    void shouldThrowConsumerClosedExceptionAfterClose() {
      // given
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.commitOffset(0, 100L))
          .isInstanceOf(ConsumerClosedException.class);
    }
  }

  @Nested
  class SendHeartbeat {

    @Test
    void shouldSendHeartbeatSuccessfully() {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"status\":\"OK\",\"generation\":1}")));

      // when / then — completes without exception
      consumer.sendHeartbeat().join();
    }

    @Test
    void shouldThrowCoordinatorUnavailableExceptionOn503() {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat"))
              .willReturn(
                  aResponse()
                      .withStatus(503)
                      .withBody("{\"status\":\"ERROR\",\"error\":\"COORDINATOR_UNAVAILABLE\"}")));

      // when / then — join() wraps in CompletionException
      assertThatThrownBy(() -> consumer.sendHeartbeat().join())
          .hasCauseInstanceOf(CoordinatorUnavailableException.class);
    }

    @Test
    void shouldThrowConsumerNotRegisteredExceptionOn400() {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat"))
              .willReturn(
                  aResponse()
                      .withStatus(400)
                      .withBody("{\"status\":\"ERROR\",\"error\":\"CONSUMER_NOT_REGISTERED\"}")));

      // when / then — join() wraps in CompletionException
      assertThatThrownBy(() -> consumer.sendHeartbeat().join())
          .hasCauseInstanceOf(ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldThrowEventBridgeExceptionOnOtherErrors() {
      // given
      stubFor(
          post(urlEqualTo("/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat"))
              .willReturn(aResponse().withStatus(500)));

      // when / then — join() wraps in CompletionException
      assertThatThrownBy(() -> consumer.sendHeartbeat().join())
          .hasCauseInstanceOf(EventBridgeException.class)
          .cause()
          .hasMessageContaining("500");
    }

    @Test
    void shouldThrowConsumerClosedExceptionAfterClose() {
      // given
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.sendHeartbeat())
          .isInstanceOf(ConsumerClosedException.class);
    }

    @Test
    void shouldUpdateGenerationFromHeartbeatResponse() {
      // given — broker returns a higher generation, signalling a rebalance occurred
      stubFor(
          post(urlEqualTo("/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"generation\":5}")));

      // when
      consumer.sendHeartbeat().join();

      // then — consumer's generation is updated so subsequent poll/commit use the new value
      assertThat(consumer.getGeneration()).isEqualTo(5L);
    }

    @Test
    void shouldKeepGenerationUnchangedWhenHeartbeatResponseOmitsIt() {
      // given — minimal 200 response with no generation field
      stubFor(
          post(urlEqualTo("/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{}")));

      // when
      consumer.sendHeartbeat().join();

      // then — generation stays at its initial value
      assertThat(consumer.getGeneration()).isEqualTo(GENERATION);
    }
  }

  @Nested
  class Close {

    @Test
    void shouldBeIdempotent() {
      // when — close twice; no exception
      consumer.close();
      consumer.close();
    }

    @Test
    void shouldPreventPollAfterClose() {
      // given
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(ConsumerClosedException.class);
    }

    @Test
    void shouldPreventCommitOffsetAfterClose() {
      // given
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.commitOffset(0, 1L))
          .isInstanceOf(ConsumerClosedException.class);
    }

    @Test
    void shouldPreventSendHeartbeatAfterClose() {
      // given
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.sendHeartbeat())
          .isInstanceOf(ConsumerClosedException.class);
    }
  }

  @Nested
  class InitialState {

    @Test
    void shouldExposeAssignedPartitions() {
      // given
      final Consumer c = new Consumer(GROUP_ID, CONSUMER_ID, List.of(2, 0, 1), GENERATION, client);

      // then — sorted ascending
      assertThat(c.getAssignedPartitions()).containsExactly(0, 1, 2);
    }

    @Test
    void shouldExposeGroupIdAndConsumerId() {
      assertThat(consumer.getGroupId()).isEqualTo(GROUP_ID);
      assertThat(consumer.getConsumerId()).isEqualTo(CONSUMER_ID);
    }

    @Test
    void shouldExposeGeneration() {
      assertThat(consumer.getGeneration()).isEqualTo(GENERATION);
    }

    @Test
    void shouldReturnUnmodifiablePartitionList() {
      // given
      final List<Integer> partitions = consumer.getAssignedPartitions();

      // when / then
      assertThatThrownBy(() -> partitions.add(99))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}

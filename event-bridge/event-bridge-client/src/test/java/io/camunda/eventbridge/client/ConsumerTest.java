/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@WireMockTest
class ConsumerTest {

  private static final String GROUP_ID = "test-group";
  private static final String CONSUMER_ID = "consumer-1";

  private static final String HEARTBEAT_URL =
      "/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/heartbeat";
  private static final String ACK_URL = "/v1/consumers/" + GROUP_ID + "/" + CONSUMER_ID + "/ack";

  private EventBridgeClient client;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo wmRuntimeInfo) {
    client = EventBridgeClient.create("http://localhost:" + wmRuntimeInfo.getHttpPort());
  }

  // -------------------------------------------------------------------------
  // SendHeartbeat

  @Nested
  class SendHeartbeat {

    @Test
    void shouldAutoRegisterOnFirstHeartbeatWithEpochZero() throws Exception {
      // given — first heartbeat: clientEpoch=0, coordinator epoch=1 → full reconcile, no partitions
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":1,\"revoke\":[],\"assign\":[],\"fullAssignment\":[]}")));
      stubAckOk();

      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, client);

      // when
      consumer.sendHeartbeat().get();

      // then
      assertThat(consumer.getCurrentEpoch()).isEqualTo(1L);
      assertThat(consumer.getOwnedPartitions()).isEmpty();
    }

    @Test
    void shouldApplyAssignDeltaAndSendAck() throws Exception {
      // given — delta: assign partitions 0 and 1 at epoch 2
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(), 2L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":2,\"revoke\":[],\"assign\":[0,1],\"fullAssignment\":[]}")));
      stubAckOk();

      // when
      consumer.sendHeartbeat().get();

      // then
      assertThat(consumer.getOwnedPartitions()).containsExactly(0, 1);
      assertThat(consumer.getCurrentEpoch()).isEqualTo(2L);
      verify(
          postRequestedFor(urlEqualTo(ACK_URL))
              .withRequestBody(equalTo("{\"epoch\":2,\"revoked\":[],\"assigned\":[0,1]}")));
    }

    @Test
    void shouldApplyRevokeDeltaAndSendAck() throws Exception {
      // given — consumer owns [0, 1]; coordinator revokes partition 1
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), 3L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":3,\"revoke\":[1],\"assign\":[],\"fullAssignment\":[]}")));
      stubAckOk();

      // when
      consumer.sendHeartbeat().get();

      // then
      assertThat(consumer.getOwnedPartitions()).containsExactly(0);
      verify(
          postRequestedFor(urlEqualTo(ACK_URL))
              .withRequestBody(equalTo("{\"epoch\":3,\"revoked\":[1],\"assigned\":[]}")));
    }

    @Test
    void shouldApplyRevokeAndAssignDeltaAndSendSingleAck() throws Exception {
      // given — revoke 1, assign 2 in the same delta
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), 4L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":4,\"revoke\":[1],\"assign\":[2],\"fullAssignment\":[]}")));
      stubAckOk();

      // when
      consumer.sendHeartbeat().get();

      // then
      assertThat(consumer.getOwnedPartitions()).containsExactly(0, 2);
      verify(1, postRequestedFor(urlEqualTo(ACK_URL)));
    }

    @Test
    void shouldNotSendAckWhenDeltaIsEmpty() throws Exception {
      // given — delta with no revoke and no assign
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 5L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":5,\"revoke\":[],\"assign\":[],\"fullAssignment\":[]}")));

      // when
      consumer.sendHeartbeat().get();

      // then — no ACK sent
      assertThat(consumer.getOwnedPartitions()).containsExactly(0);
      verify(0, postRequestedFor(urlEqualTo(ACK_URL)));
    }

    @Test
    void shouldApplyFullAssignmentOnEpochAdvanceAndSendAck() throws Exception {
      // given — consumer owns [0, 1] at epoch 3; coordinator advanced to epoch 4 with new
      // assignment
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), 3L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":4,\"revoke\":[],\"assign\":[],\"fullAssignment\":[1,2]}")));
      stubAckOk();

      // when
      consumer.sendHeartbeat().get();

      // then — ownedPartitions replaced wholesale; epoch updated
      assertThat(consumer.getOwnedPartitions()).containsExactly(1, 2);
      assertThat(consumer.getCurrentEpoch()).isEqualTo(4L);
      // ACK: revoked = [0] (was owned, not in fullAssignment); assigned = [1, 2]
      verify(1, postRequestedFor(urlEqualTo(ACK_URL)));
    }

    @Test
    void shouldApplyEmptyFullAssignmentOnEpochAdvance() throws Exception {
      // given — consumer owns [0] at epoch 2; new epoch with no partitions assigned
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 2L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":3,\"revoke\":[],\"assign\":[],\"fullAssignment\":[]}")));
      stubAckOk();

      // when
      consumer.sendHeartbeat().get();

      // then
      assertThat(consumer.getOwnedPartitions()).isEmpty();
      assertThat(consumer.getCurrentEpoch()).isEqualTo(3L);
      // ACK sent: revoked=[0], assigned=[]
      verify(1, postRequestedFor(urlEqualTo(ACK_URL)));
    }

    @Test
    void shouldIgnoreStaleHeartbeatResponse() throws Exception {
      // given — consumer is at epoch 5; server returns epoch 3 (stale)
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 5L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":3,\"revoke\":[],\"assign\":[1],\"fullAssignment\":[]}")));

      // when
      consumer.sendHeartbeat().get();

      // then — no state change
      assertThat(consumer.getCurrentEpoch()).isEqualTo(5L);
      assertThat(consumer.getOwnedPartitions()).containsExactly(0);
      verify(0, postRequestedFor(urlEqualTo(ACK_URL)));
    }

    @Test
    void shouldThrowCoordinatorUnavailableOn503() {
      // given
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"UNAVAILABLE\"}")));

      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, client);

      // when / then
      assertThatThrownBy(() -> consumer.sendHeartbeat().get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorUnavailableException.class);
    }

    @Test
    void shouldThrowEventBridgeExceptionOnOtherErrors() {
      // given
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"INTERNAL\"}")));

      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, client);

      // when / then
      assertThatThrownBy(() -> consumer.sendHeartbeat().get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(EventBridgeException.class)
          .hasMessageContaining("500");
    }

    @Test
    void shouldContinueWhenAckReturnsNon200() throws Exception {
      // given — heartbeat assigns partition 0; ACK returns 503 (non-fatal per spec)
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(), 1L, client);
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":1,\"revoke\":[],\"assign\":[0],\"fullAssignment\":[]}")));
      stubFor(post(urlEqualTo(ACK_URL)).willReturn(aResponse().withStatus(503)));

      // when — should complete without throwing; local state is already updated
      consumer.sendHeartbeat().get();

      // then
      assertThat(consumer.getOwnedPartitions()).containsExactly(0);
    }
  }

  // -------------------------------------------------------------------------
  // Poll

  @Nested
  class Poll {

    @Test
    void shouldReturnEmptyListWhenNoEventsAvailable() {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);
      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[],\"nextPosition\":100,\"epoch\":1}")));

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
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);
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
                              + "],\"nextPosition\":1002,\"epoch\":1}")));

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
      // given — consumer with partitions 0 and 1
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), 1L, client);

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
                              + "],\"nextPosition\":101,\"epoch\":1}")));

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
                              + "],\"nextPosition\":201,\"epoch\":1}")));

      // when
      final List<Event> events = consumer.poll(10, Duration.ZERO);

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
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(2, 0, 1), 1L, client);

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":10,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":11,\"epoch\":1}")));
      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":20,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":21,\"epoch\":1}")));
      stubFor(
          get(urlMatching("/v1/events/2/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":30,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":31,\"epoch\":1}")));

      // when
      final List<Event> events = consumer.poll(10, Duration.ZERO);

      // then — events arrive in partition ID order (0, 1, 2)
      assertThat(events).hasSize(3);
      assertThat(events.get(0).partitionId()).isEqualTo(0);
      assertThat(events.get(1).partitionId()).isEqualTo(1);
      assertThat(events.get(2).partitionId()).isEqualTo(2);
    }

    @Test
    void shouldTrackNextPositionBetweenPolls() {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);

      stubFor(
          get(urlMatching("/v1/events/0/poll\\?.*fromPosition=-1.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":100,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":101,\"epoch\":1}")));
      stubFor(
          get(urlMatching("/v1/events/0/poll\\?.*fromPosition=101.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":101,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":102,\"epoch\":1}")));

      // when
      consumer.poll(10, Duration.ZERO);
      final List<Event> secondPoll = consumer.poll(10, Duration.ZERO);

      // then — second poll starts from nextPosition returned by first
      assertThat(secondPoll).hasSize(1);
      assertThat(secondPoll.get(0).position()).isEqualTo(101L);
    }

    @Test
    void shouldReturnPartialResultsWhenRebalanceInProgressIsReceived() {
      // given — partition 0 returns events; partition 1 returns REBALANCE_IN_PROGRESS
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0, 1), 1L, client);

      stubFor(
          get(urlMatching("/v1/events/0/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"status\":\"OK\",\"events\":[{\"position\":10,\"payload\":\"AA==\"}],"
                              + "\"nextPosition\":11,\"epoch\":1}")));
      stubFor(
          get(urlMatching("/v1/events/1/poll.*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"status\":\"REBALANCE_IN_PROGRESS\",\"epoch\":2}")));

      // when — no exception thrown; rebalances are now invisible at poll level
      final List<Event> events = consumer.poll(10, Duration.ZERO);

      // then — events from partition 0 are returned; partition 1 loop was aborted
      assertThat(events).hasSize(1);
      assertThat(events.get(0).partitionId()).isEqualTo(0);
      // epoch is NOT updated by poll; sendHeartbeat() will reconcile on next call
      assertThat(consumer.getCurrentEpoch()).isEqualTo(1L);
    }

    @Test
    void shouldThrowConsumerClosedExceptionAfterClose() {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);
      consumer.close();

      // when / then
      assertThatThrownBy(() -> consumer.poll(10, Duration.ZERO))
          .isInstanceOf(ConsumerClosedException.class);
    }
  }

  // -------------------------------------------------------------------------
  // CommitOffset

  @Nested
  class CommitOffset {

    @Test
    void shouldCommitOffsetWithoutGenerationField() throws Exception {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);
      stubFor(post(urlEqualTo("/v1/events/0/commit")).willReturn(aResponse().withStatus(200)));

      // when
      consumer.commitOffset(0, 42L).get();

      // then — request body must NOT contain a "generation" field
      verify(
          postRequestedFor(urlEqualTo("/v1/events/0/commit"))
              .withRequestBody(
                  equalTo(
                      "{\"groupId\":\"test-group\",\"consumerId\":\"consumer-1\",\"position\":42}")));
    }

    @Test
    void shouldReRegisterAndRetryOnConsumerNotRegistered() throws Exception {
      // given — first commit returns 404; heartbeat succeeds; retry commit succeeds
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);

      stubFor(
          post(urlEqualTo("/v1/events/0/commit"))
              .inScenario("retry")
              .whenScenarioStateIs("Started")
              .willReturn(aResponse().withStatus(404))
              .willSetStateTo("after-first-commit"));

      stubFor(
          post(urlEqualTo("/v1/events/0/commit"))
              .inScenario("retry")
              .whenScenarioStateIs("after-first-commit")
              .willReturn(aResponse().withStatus(200)));

      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":2,\"revoke\":[],\"assign\":[],\"fullAssignment\":[0]}")));
      stubAckOk();

      // when
      consumer.commitOffset(0, 42L).get();

      // then — heartbeat was called once (to re-register); commit was retried
      verify(postRequestedFor(urlEqualTo(HEARTBEAT_URL)));
    }

    @Test
    void shouldPropagateExceptionIfRetryAlsoFails() {
      // given — both attempts return 404
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, List.of(0), 1L, client);

      stubFor(post(urlEqualTo("/v1/events/0/commit")).willReturn(aResponse().withStatus(404)));
      stubFor(
          post(urlEqualTo(HEARTBEAT_URL))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"epoch\":2,\"revoke\":[],\"assign\":[],\"fullAssignment\":[]}")));
      stubAckOk();

      // when / then
      assertThatThrownBy(() -> consumer.commitOffset(0, 42L).get())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(ConsumerNotRegisteredException.class);
    }
  }

  // -------------------------------------------------------------------------
  // Close

  @Nested
  class Close {

    @Test
    void shouldThrowConsumerClosedExceptionOnHeartbeatAfterClose() {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, client);
      consumer.close();

      // when / then — checkNotClosed() fires synchronously before runAsync; exception is direct
      assertThatThrownBy(() -> consumer.sendHeartbeat())
          .isInstanceOf(ConsumerClosedException.class);
    }

    @Test
    void shouldThrowConsumerClosedExceptionOnCommitAfterClose() {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, client);
      consumer.close();

      // when / then — checkNotClosed() fires synchronously before runAsync; exception is direct
      assertThatThrownBy(() -> consumer.commitOffset(0, 1L))
          .isInstanceOf(ConsumerClosedException.class);
    }

    @Test
    void shouldBeIdempotent() {
      // given
      final Consumer consumer = new Consumer(GROUP_ID, CONSUMER_ID, client);

      // when / then — second close must not throw
      consumer.close();
      consumer.close();
    }
  }

  // -------------------------------------------------------------------------
  // Helpers

  private static void stubAckOk() {
    stubFor(
        post(urlEqualTo(ACK_URL))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"OK\"}")));
  }
}

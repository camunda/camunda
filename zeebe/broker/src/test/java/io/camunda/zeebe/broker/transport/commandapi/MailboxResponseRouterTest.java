/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class MailboxResponseRouterTest {

  private static final GatewayId GATEWAY = new GatewayId("gateway-1");

  private final ServerOutput transport = mock(ServerOutput.class);
  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final MailboxResponseRouter router =
      new MailboxResponseRouter(transport, store, Runnable::run);

  @Test
  void shouldSendTheResponseToTheOutboxOfTheGatewayThatSentTheRequest() {
    // given
    final var requestId = router.register(GATEWAY, 7);

    // when
    router.sendResponse(new FakeResponse(requestId, new byte[] {1, 2, 3}));

    // then
    final var responses = store.takeResponses(GATEWAY, Set.of(7L));
    assertThat(responses).extracting(OutboxResponse::payload).containsExactly(new byte[] {1, 2, 3});
    verify(transport, never()).sendResponse(any());
  }

  @Test
  void shouldSendResponsesToOtherRequestsThroughTheTransport() {
    // given
    final var response = new FakeResponse(42, new byte[] {1});

    // when
    router.sendResponse(response);

    // then
    verify(transport).sendResponse(response);
    assertThat(store.takeResponses(GATEWAY, Set.of(42L))).isEmpty();
  }

  @Test
  void shouldAnswerARequestOnlyOnce() {
    // given
    final var requestId = router.register(GATEWAY, 7);
    router.sendResponse(new FakeResponse(requestId, new byte[] {1}));
    final var second = new FakeResponse(requestId, new byte[] {2});

    // when
    router.sendResponse(second);

    // then
    verify(transport).sendResponse(second);
    assertThat(router.pendingCount()).isZero();
  }

  @Test
  void shouldCopyTheResponseBeforeItIsHandedToTheExecutor() {
    // given
    final var queued = new ArrayDeque<Runnable>();
    final var queueingRouter = new MailboxResponseRouter(transport, store, queued::add);
    final var requestId = queueingRouter.register(GATEWAY, 7);
    final var response = new FakeResponse(requestId, new byte[] {1, 2, 3});

    // when - the writer reuses its response object before the executor runs
    queueingRouter.sendResponse(response);
    response.payload[0] = 9;
    queued.poll().run();

    // then
    final var responses = store.takeResponses(GATEWAY, Set.of(7L));
    assertThat(responses).extracting(OutboxResponse::payload).containsExactly(new byte[] {1, 2, 3});
  }

  @Test
  void shouldNotFailWhenTheStoreFails() {
    // given
    final var failingStore = mock(MailboxStore.class);
    doThrow(new MailboxStoreException("down", new RuntimeException()))
        .when(failingStore)
        .putResponse(any());
    final var failingRouter = new MailboxResponseRouter(transport, failingStore, Runnable::run);
    final var requestId = failingRouter.register(GATEWAY, 7);

    // when / then
    assertThatCode(() -> failingRouter.sendResponse(new FakeResponse(requestId, new byte[] {1})))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldHandOutIncreasingRequestIdsThatTheTransportDoesNotUse() {
    // when
    final var first = router.register(GATEWAY, 1);
    final var second = router.register(GATEWAY, 2);

    // then
    assertThat(first).isGreaterThanOrEqualTo(MailboxResponseRouter.FIRST_REQUEST_ID);
    assertThat(second).isGreaterThan(first);
  }

  @Test
  void shouldHandOutIdsThatTheLeadersOfOtherTermsDoNotUse() {
    // given - the old leader of term 1 and the new leader of term 2 are two nodes
    final var oldLeader = new MailboxResponseRouter(transport, store, Runnable::run);
    final var newLeader = new MailboxResponseRouter(transport, store, Runnable::run);
    oldLeader.startTerm(1);
    newLeader.startTerm(2);

    // when
    final var oldId = oldLeader.register(GATEWAY, 1);
    final var newId = newLeader.register(GATEWAY, 2);

    // then
    assertThat(oldId).isGreaterThanOrEqualTo(MailboxResponseRouter.FIRST_REQUEST_ID);
    assertThat(newId).isGreaterThanOrEqualTo(MailboxResponseRouter.FIRST_REQUEST_ID);
    assertThat(newId).isNotEqualTo(oldId);
  }

  @Test
  void shouldNotSendTheResponseToACommandOfTheOldLeaderToTheGatewayOfTheNewLeader() {
    // given - the old leader wrote a command, and the new leader replays it
    final var oldLeader = new MailboxResponseRouter(transport, store, Runnable::run);
    oldLeader.startTerm(1);
    final var commandOfOldLeader = oldLeader.register(new GatewayId("gateway-old"), 1);
    final var newLeader = new MailboxResponseRouter(transport, store, Runnable::run);
    newLeader.startTerm(2);
    newLeader.register(GATEWAY, 7);

    // when - the engine of the new leader answers the replayed command
    final var lateResponse = new FakeResponse(commandOfOldLeader, new byte[] {1});
    newLeader.sendResponse(lateResponse);

    // then
    assertThat(store.takeResponses(GATEWAY, Set.of(7L))).isEmpty();
    assertThat(newLeader.pendingCount()).isEqualTo(1);
    verify(transport).sendResponse(lateResponse);
  }

  @Test
  void shouldForgetTheRequestsOfThePreviousTermWhenANewTermStarts() {
    // given
    router.startTerm(1);
    router.register(GATEWAY, 1);

    // when
    router.startTerm(3);

    // then
    assertThat(router.pendingCount()).isZero();
  }

  @Test
  void shouldForgetARequest() {
    // given
    final var requestId = router.register(GATEWAY, 7);

    // when
    router.forget(requestId);
    final var response = new FakeResponse(requestId, new byte[] {1});
    router.sendResponse(response);

    // then
    verify(transport).sendResponse(response);
  }

  @Test
  void shouldForgetRequestsThatWaitedLongerThanTheTimeToLive() {
    // given
    final var now = new AtomicLong();
    final var expiring =
        new MailboxResponseRouter(transport, store, Runnable::run, Duration.ofMinutes(1), now::get);
    final var old = expiring.register(GATEWAY, 1);

    // when - the engine never answers, and later another request arrives
    now.addAndGet(Duration.ofMinutes(2).toNanos());
    expiring.register(GATEWAY, 2);

    // then
    assertThat(expiring.pendingCount()).isEqualTo(1);
    final var late = new FakeResponse(old, new byte[] {1});
    expiring.sendResponse(late);
    verify(transport).sendResponse(late);
  }

  @Test
  void shouldKeepRequestsThatAreWithinTheTimeToLive() {
    // given
    final var now = new AtomicLong();
    final var expiring =
        new MailboxResponseRouter(transport, store, Runnable::run, Duration.ofMinutes(1), now::get);
    expiring.register(GATEWAY, 1);

    // when
    now.addAndGet(Duration.ofSeconds(30).toNanos());
    expiring.register(GATEWAY, 2);

    // then
    assertThat(expiring.pendingCount()).isEqualTo(2);
  }

  @Test
  void shouldNotAnswerARequestThatWasTurnedAwayBecauseThisNodeDoesNotLead() {
    // given
    final var requestId = router.register(GATEWAY, 7);
    final var writer = new ErrorResponseWriter();
    writer.partitionLeaderMismatch(1);
    final var response = new ErrorBytes(requestId, writer);

    // when
    router.sendResponse(response);

    // then
    assertThat(store.takeResponses(GATEWAY, Set.of(7L))).isEmpty();
    assertThat(router.takeNotLeading(requestId)).isTrue();
    assertThat(router.takeNotLeading(requestId)).isFalse();
    verify(transport, never()).sendResponse(any());
  }

  @Test
  void shouldAnswerARequestThatFailedForAnotherReason() {
    // given
    final var requestId = router.register(GATEWAY, 7);
    final var writer = new ErrorResponseWriter();
    writer.errorCode(ErrorCode.RESOURCE_EXHAUSTED).errorMessage("full");

    // when
    router.sendResponse(new ErrorBytes(requestId, writer));

    // then
    assertThat(store.takeResponses(GATEWAY, Set.of(7L))).hasSize(1);
    assertThat(router.takeNotLeading(requestId)).isFalse();
  }

  @Test
  void shouldForgetAllRequestsWhenCleared() {
    // given
    router.register(GATEWAY, 1);
    router.register(GATEWAY, 2);

    // when
    router.clear();

    // then
    assertThat(router.pendingCount()).isZero();
  }

  /** The bytes of an error response, as the request handler sends them. */
  private record ErrorBytes(long requestIdValue, ErrorResponseWriter writer)
      implements ServerResponse {
    @Override
    public long getRequestId() {
      return requestIdValue;
    }

    @Override
    public int getPartitionId() {
      return 1;
    }

    @Override
    public int getLength() {
      return writer.getLength();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return writer.write(buffer, offset);
    }
  }

  private static final class FakeResponse implements ServerResponse {
    private final long requestId;
    private final byte[] payload;

    private FakeResponse(final long requestId, final byte[] payload) {
      this.requestId = requestId;
      this.payload = payload;
    }

    @Override
    public long getRequestId() {
      return requestId;
    }

    @Override
    public int getPartitionId() {
      return 1;
    }

    @Override
    public int getLength() {
      return payload.length;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, payload);
      return payload.length;
    }
  }
}

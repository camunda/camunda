/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.FetchResult;
import io.camunda.eventbridge.client.TopicPartition;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background fetcher that keeps up to a configurable prefetch depth of long-poll fetches in flight
 * per owned partition, filling the {@link PrefetchBuffer}. Runs on the client's shared scheduled
 * executor via asynchronous {@code fetchFromTopic(...).whenComplete(...)} callbacks — no dedicated
 * thread.
 *
 * <p>{@link #kick()} is idempotent and cheap to call often: it claims every owned partition still
 * below its prefetch depth (fewer buffered batches plus in-flight fetches than {@code
 * prefetchDepth}) and issues a long-poll fetch for it. With depth {@code 1} a partition re-fetches
 * only once its buffer drains; a higher depth lets that many fetches pipeline. When a fetch
 * completes, its result is buffered (waking a parked poll), an empty parked long-poll re-arms
 * immediately, and a failed or out-of-range fetch is retried (immediately on reset, after a backoff
 * on error). A fetch whose generation no longer matches — because a seek or reassignment happened
 * while it was in flight — is discarded.
 */
public final class Prefetcher {

  private static final Logger LOG = LoggerFactory.getLogger(Prefetcher.class);

  /** Backoff before re-fetching a partition whose fetch failed (e.g. a leadership move). */
  private static final long FETCH_ERROR_BACKOFF_MS = 500L;

  private final Fetcher fetcher;
  private final ScheduledExecutorService executor;
  private final SubscriptionState subscription;
  private final PrefetchBuffer buffer;
  private final BooleanSupplier closed;

  /** Max bytes requested per (topic, partition) fetch. */
  private final int fetchMaxBytes;

  /** Min committed bytes the broker waits to accumulate before responding. */
  private final int fetchMinBytes;

  /**
   * How long a background fetch parks on the broker (its {@code maxWaitMs}) before returning empty.
   * An idle partition issues at most one fetch per this interval — no client spin — while the
   * broker wakes it the instant data is committed (low latency when active).
   */
  private final long longPollMs;

  public Prefetcher(
      final Fetcher fetcher,
      final ScheduledExecutorService executor,
      final SubscriptionState subscription,
      final PrefetchBuffer buffer,
      final BooleanSupplier closed,
      final int fetchMaxBytes,
      final int fetchMinBytes,
      final long longPollMs) {
    this.fetcher = fetcher;
    this.executor = executor;
    this.subscription = subscription;
    this.buffer = buffer;
    this.closed = closed;
    this.fetchMaxBytes = fetchMaxBytes;
    this.fetchMinBytes = fetchMinBytes;
    this.longPollMs = longPollMs;
  }

  /**
   * Ensures every owned partition still below its prefetch depth has a long-poll fetch issued.
   * Idempotent and cheap to call often (on poll, after each fetch completes): the in-flight count
   * plus buffered batches are bounded by the prefetch depth per partition.
   */
  public void kick() {
    if (closed.getAsBoolean()) {
      return;
    }
    final PrefetchBuffer.Claim claim = buffer.claim(subscription.ownedPartitions());
    for (final TopicPartition tp : claim.partitions()) {
      issueFetch(tp, claim.generation());
    }
  }

  /** Issues a single long-poll fetch for {@code tp} from its current fetch cursor. */
  private void issueFetch(final TopicPartition tp, final int gen) {
    long from = subscription.nextPosition(tp);
    if (from == SubscriptionState.UNSET_POSITION) {
      from = subscription.resolveStartPosition(tp);
      subscription.setNextPosition(tp, from);
    }
    final long fromPosition = from;
    fetcher
        .fetchFromTopic(
            tp.topic(), tp.partition(), fromPosition, fetchMaxBytes, fetchMinBytes, longPollMs)
        .whenComplete((result, error) -> onFetchComplete(tp, gen, fromPosition, result, error));
  }

  /** Handles a completed background fetch: buffer new events, then re-arm. */
  private void onFetchComplete(
      final TopicPartition tp,
      final int gen,
      final long fromPosition,
      final FetchResult result,
      final Throwable error) {
    final boolean[] retry = {false, false}; // [retryNow, retryDelayed]
    buffer.runLocked(
        () -> {
          buffer.clearInFlight(tp);
          if (gen != buffer.generation() || !subscription.owns(tp)) {
            return; // a seek/reassignment happened while this fetch was in flight — discard result
          }
          if (error != null) {
            LOG.debug("Fetch failed for {}; will retry", tp, error);
            retry[1] = true;
          } else if (result != null && result.isSuccess()) {
            long next = fromPosition;
            boolean any = false;
            for (final var entry : result.entries(fromPosition)) {
              buffer.add(
                  tp, new Event(entry.position(), tp.topic(), tp.partition(), entry.value()));
              next = entry.position() + 1;
              any = true;
            }
            if (!any) {
              // Parked long-poll returned empty (still at the tip) — re-arm immediately; the
              // emptiness already cost LONG_POLL_MS of server-side waiting, so this is not a spin.
              retry[0] = true;
            } else {
              subscription.setNextPosition(tp, next);
              buffer.signal();
            }
          } else if (result != null && result.isOutOfRange()) {
            // Cursor below the earliest retained record, or an earliest-fetch on a still-empty
            // partition. Mark unresolved so the next fetch re-applies the reset policy (resolved
            // off-lock in issueFetch), and back off rather than retrying immediately: when the
            // reset
            // resolves to the same out-of-range position (e.g. a freshly created topic with no
            // records yet), an immediate retry would hot-spin until the first record appears.
            LOG.warn("Fetch out of range for {} at {}; resetting", tp, fromPosition);
            subscription.setNextPosition(tp, SubscriptionState.UNSET_POSITION);
            retry[1] = true;
          } else {
            LOG.debug(
                "Fetch failed for {}: {}; will retry",
                tp,
                result == null ? "no result" : result.error());
            retry[1] = true;
          }
        });
    if (retry[0]) {
      kick();
    } else if (retry[1] && !closed.getAsBoolean()) {
      executor.schedule(this::kick, FETCH_ERROR_BACKOFF_MS, TimeUnit.MILLISECONDS);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.policy.RequestRetryCondition;
import com.azure.core.http.policy.RetryStrategy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Retries a vault request only on a transient failure, with an exponential backoff, and only while
 * the waits fit in half of the call timeout.
 *
 * <p>Transient means an answer of 408, 429, 500, 502, 503 or 504, or a network error or timeout
 * without an answer. The SDK's own condition retries every exception instead, so a sign-in that
 * Microsoft Entra ID rejected would be requested again on every retry, and an interrupted call
 * would keep going. Neither is retried here.
 *
 * <p>The n-th retry waits up to {@code baseDelay * 2^(n-1)}, the base delay chosen so that {@code
 * maxRetries} retries wait at most half of the call timeout: the SDK's defaults (0.8s doubling,
 * 5.6s over three retries) would outlast the default 5s call timeout, and a vault failing fast
 * would be reported as a timeout, without its answer. Each wait is drawn between half and all of
 * that delay, so that the reads a {@link io.camunda.secretstore.ConcurrentSecretStore} fans out do
 * not retry in lockstep. The SDK waits as long as a {@code Retry-After} header asks, which this
 * strategy cannot shorten; every wait, backoff or {@code Retry-After}, counts against the budget of
 * its request, and a retry whose wait no longer fits is not made, so the vault's answer is reported
 * rather than replaced by a call timeout.
 *
 * <p>The budget left is tracked per thread: the vault is read with the synchronous client, whose
 * pipeline retries a request on the thread that sent it, and asks this strategy with a try count of
 * 0 first for every request.
 */
final class KeyVaultRetryStrategy implements RetryStrategy {

  // the SDK's own base delay, kept when it already fits
  private static final long SDK_BASE_DELAY_MILLIS = 800;

  private static final Set<Integer> TRANSIENT_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

  private final int maxRetries;
  private final long budgetMillis;
  private final long baseDelayMillis;
  // the waits granted so far to the request this thread is sending, in milliseconds
  private final ThreadLocal<long[]> waited = ThreadLocal.withInitial(() -> new long[1]);

  KeyVaultRetryStrategy(final int maxRetries, final Duration callTimeout) {
    this.maxRetries = maxRetries;
    budgetMillis = TimeUnit.MILLISECONDS.convert(callTimeout) / 2;
    // the SDK sleeps in whole milliseconds, so a shorter delay would not wait at all
    baseDelayMillis =
        Math.max(
            1,
            Math.min(
                SDK_BASE_DELAY_MILLIS, budgetMillis / Math.max(1, exponentialSum(maxRetries))));
  }

  @Override
  public int getMaxRetries() {
    return maxRetries;
  }

  @Override
  public Duration calculateRetryDelay(final int retryAttempts) {
    final long delay = nominalDelay(retryAttempts);
    return Duration.ofMillis(ThreadLocalRandom.current().nextLong((delay + 1) / 2, delay + 1));
  }

  @Override
  public boolean shouldRetryCondition(final RequestRetryCondition condition) {
    final var waitedMillis = waited.get();
    if (condition.getTryCount() == 0) {
      waitedMillis[0] = 0;
    }
    if (Thread.currentThread().isInterrupted()) {
      return false;
    }
    final long wait;
    final var response = condition.getResponse();
    if (response != null) {
      if (!TRANSIENT_STATUSES.contains(response.getStatusCode())) {
        return false;
      }
      final var retryAfter = retryAfter(response.getHeaders());
      wait =
          retryAfter == null
              ? nominalDelay(condition.getTryCount())
              : Math.max(0, TimeUnit.MILLISECONDS.convert(retryAfter));
    } else if (AzureKeyVaultErrors.isTransportFailure(condition.getThrowable())) {
      wait = nominalDelay(condition.getTryCount());
    } else {
      return false;
    }
    if (wait > budgetMillis - waitedMillis[0]) {
      return false;
    }
    waitedMillis[0] += wait;
    return true;
  }

  // the longest the backoff waits before the given retry
  private long nominalDelay(final int retryAttempts) {
    return Math.min(budgetMillis, scaled(1L << Math.min(retryAttempts, 62)));
  }

  // 2^n - 1, the sum of the first n powers of two, saturating
  private static long exponentialSum(final int n) {
    return n >= 63 ? Long.MAX_VALUE : (1L << n) - 1;
  }

  private long scaled(final long factor) {
    return factor > Long.MAX_VALUE / baseDelayMillis ? Long.MAX_VALUE : factor * baseDelayMillis;
  }

  /** The wait the vault asked for, read from the same headers the SDK honours. */
  private static @Nullable Duration retryAfter(final HttpHeaders headers) {
    for (final var millisHeader :
        new HttpHeaderName[] {HttpHeaderName.RETRY_AFTER_MS, HttpHeaderName.X_MS_RETRY_AFTER_MS}) {
      final var millis = parseLong(headers.getValue(millisHeader));
      if (millis != null) {
        return Duration.ofMillis(millis);
      }
    }
    final var value = headers.getValue(HttpHeaderName.RETRY_AFTER);
    if (value == null) {
      return null;
    }
    final var seconds = parseLong(value);
    if (seconds != null) {
      return Duration.ofSeconds(seconds);
    }
    try {
      return Duration.between(
          OffsetDateTime.now(), OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
    } catch (final DateTimeParseException e) {
      return null;
    }
  }

  private static @Nullable Long parseLong(final @Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value.strip());
    } catch (final NumberFormatException e) {
      return null;
    }
  }
}

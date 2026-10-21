/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.segment.LogId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Announces each upload with {@code POST /v1/uploads} of an {@link UploadNotice}, in the
 * background. A request that fails, times out or isn't answered with 202 is logged and not sent
 * again, and so is an announcement that finds too many requests in flight: workers also list the
 * store, so a lost announcement only delays them.
 */
public final class HttpAnnouncer implements Announcer {

  private static final Logger LOG = LoggerFactory.getLogger(HttpAnnouncer.class);

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration CLOSE_WAIT = Duration.ofSeconds(2);
  private static final int MAX_IN_FLIGHT = 64;
  private static final int ACCEPTED = 202;

  private final URI uploads;
  private final HttpClient client;
  private final Duration requestTimeout;
  private final Duration closeWait;
  private final int maxInFlight;
  private final Semaphore permits;
  private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;

  /** Announces to the service at {@code serviceUrl}, such as {@code http://host:8080}. */
  public HttpAnnouncer(final URI serviceUrl) {
    this(serviceUrl, REQUEST_TIMEOUT, CLOSE_WAIT, MAX_IN_FLIGHT);
  }

  /**
   * @param requestTimeout how long a request may take before it counts as failed
   * @param closeWait how long {@link #close()} waits for the requests in flight
   * @param maxInFlight how many requests may be in flight; more announcements are dropped
   */
  HttpAnnouncer(
      final URI serviceUrl,
      final Duration requestTimeout,
      final Duration closeWait,
      final int maxInFlight) {
    uploads = URI.create(withoutTrailingSlashes(serviceUrl.toString()) + "/v1/uploads");
    client = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    this.requestTimeout = requestTimeout;
    this.closeWait = closeWait;
    this.maxInFlight = maxInFlight;
    permits = new Semaphore(maxInFlight);
  }

  @Override
  public void announce(final LogId log, final Upload upload) {
    if (closed) {
      LOG.warn("Expected an open announcer to announce segment {}, but it is closed", upload.key());
      return;
    }
    if (!permits.tryAcquire()) {
      LOG.warn(
          "Expected at most {} announcements in flight to {}, but got more: dropping the one of"
              + " segment {}",
          maxInFlight,
          uploads,
          upload.key());
      return;
    }
    try {
      send(notice(log, upload));
    } catch (final RuntimeException e) {
      permits.release();
      LOG.warn(
          "Expected to announce segment {} to {}, but couldn't make the request",
          upload.key(),
          uploads,
          e);
    }
  }

  /** Waits briefly for the announcements in flight, as before a process exits. */
  @Override
  public void close() {
    closed = true;
    try {
      CompletableFuture.allOf(inFlight.toArray(CompletableFuture[]::new))
          .get(closeWait.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final ExecutionException | TimeoutException e) {
      // Each failure was logged; what didn't finish, workers find by listing the store.
    } finally {
      client.shutdownNow();
    }
  }

  /** How many announcements are in flight. */
  int inFlight() {
    return maxInFlight - permits.availablePermits();
  }

  private void send(final UploadNotice notice) {
    final var request =
        HttpRequest.newBuilder(uploads)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(notice.toJson()))
            .build();
    final var sent =
        client
            .sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .whenComplete((response, failure) -> log(notice, response, failure));
    inFlight.add(sent);
    sent.whenComplete(
        (response, failure) -> {
          inFlight.remove(sent);
          permits.release();
        });
  }

  private void log(
      final UploadNotice notice,
      final @Nullable HttpResponse<Void> response,
      final @Nullable Throwable failure) {
    if (failure != null) {
      LOG.warn(
          "Expected {} to take the announcement of segment {}, but sending it failed",
          uploads,
          notice.key(),
          failure);
    } else if (response != null && response.statusCode() != ACCEPTED) {
      LOG.warn(
          "Expected status {} from {} for the announcement of segment {}, but got {}",
          ACCEPTED,
          uploads,
          notice.key(),
          response.statusCode());
    }
  }

  private static UploadNotice notice(final LogId log, final Upload upload) {
    final var footer = upload.footer();
    return new UploadNotice(
        log.cluster(),
        log.tenant(),
        log.partition(),
        footer.history(),
        upload.key(),
        footer.firstIndex(),
        footer.lastIndex(),
        footer.firstPosition(),
        footer.lastPosition(),
        upload.size());
  }

  private static String withoutTrailingSlashes(final String url) {
    int end = url.length();
    while (end > 0 && url.charAt(end - 1) == '/') {
      end--;
    }
    return url.substring(0, end);
  }
}

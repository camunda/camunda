/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.segment.LogId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One stream of server-sent events from the service, {@code GET /v1/notifications?log=…}, for the
 * logs a worker holds. It connects again when the logs change, and a while after the stream drops.
 * Nothing depends on it: readers list at their interval anyway.
 */
final class Notifications implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(Notifications.class);

  private final String service;
  private final Consumer<UploadNotice> onUpload;
  private final Runnable onConnected;
  private final long retryNanos;
  private final HttpClient client;
  private final ScheduledExecutorService scheduler;

  // Guarded by this.
  private Set<LogId> wanted = Set.of();
  private @Nullable Connection connection;
  private boolean closed;

  /**
   * @param onConnected runs whenever a stream opens: the uploads announced while there was none are
   *     lost, so a worker reads what it may have missed
   */
  Notifications(
      final URI service,
      final Consumer<UploadNotice> onUpload,
      final Runnable onConnected,
      final Duration retryDelay) {
    final var base = service.toString();
    this.service = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    this.onUpload = onUpload;
    this.onConnected = onConnected;
    retryNanos = retryDelay.toNanos();
    client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("event-plane-notifications").factory());
  }

  /** Listens for the uploads of these logs from now on, on a new stream if they changed. */
  synchronized void subscribe(final Set<LogId> logs) {
    if (closed || logs.equals(wanted)) {
      // A stream that dropped is opened again by the reconnect that its end scheduled.
      return;
    }
    wanted = Set.copyOf(logs);
    connect();
  }

  @Override
  public void close() {
    synchronized (this) {
      closed = true;
      if (connection != null) {
        connection.cancel();
        connection = null;
      }
    }
    scheduler.shutdownNow();
    client.shutdownNow();
  }

  private synchronized void connect() {
    if (connection != null) {
      connection.cancel();
      connection = null;
    }
    if (closed || wanted.isEmpty()) {
      return;
    }
    final var opened = new Connection();
    connection = opened;
    final var request =
        HttpRequest.newBuilder(uri(wanted)).header("Accept", "text/event-stream").GET().build();
    LOG.debug("Listening for uploads at {}", request.uri());
    client
        .sendAsync(
            request,
            info -> {
              if (info.statusCode() != 200) {
                return refused(info.statusCode());
              }
              onConnected.run();
              return BodySubscribers.<Connection, Void>fromLineSubscriber(
                  opened, ignored -> null, StandardCharsets.UTF_8, null);
            })
        .whenComplete((response, error) -> ended(opened, error));
  }

  private static BodySubscriber<Void> refused(final int status) {
    LOG.debug("Expected status 200 for the stream of uploads, but got {}", status);
    return BodySubscribers.replacing((Void) null);
  }

  /** Connects again a while after the current stream ended, unless something replaced it. */
  private synchronized void ended(final Connection ended, @Nullable final Throwable error) {
    if (connection != ended || closed) {
      return;
    }
    connection = null;
    if (error != null) {
      LOG.debug("The stream of uploads failed; connecting again", error);
    }
    scheduler.schedule(this::reconnect, retryNanos, TimeUnit.NANOSECONDS);
  }

  private synchronized void reconnect() {
    if (connection == null) {
      connect();
    }
  }

  private URI uri(final Set<LogId> logs) {
    return URI.create(
        service
            + "/v1/notifications?"
            + logs.stream()
                .map(LogId::toString)
                .sorted(Comparator.naturalOrder())
                .map(log -> "log=" + URLEncoder.encode(log, StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
  }

  /** The lines of one stream, read as server-sent events. */
  private final class Connection implements Flow.Subscriber<String> {

    private final StringBuilder data = new StringBuilder();
    private @Nullable String event;
    private volatile Flow.@Nullable Subscription subscription;
    private volatile boolean cancelled;

    void cancel() {
      cancelled = true;
      final var current = subscription;
      if (current != null) {
        current.cancel();
      }
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
      this.subscription = subscription;
      if (cancelled) {
        subscription.cancel();
      } else {
        subscription.request(Long.MAX_VALUE);
      }
    }

    @Override
    public void onNext(final String line) {
      if (cancelled || line.startsWith(":")) {
        return;
      }
      if (line.isEmpty()) {
        dispatch();
      } else if (line.startsWith("event:")) {
        event = value(line, "event:");
      } else if (line.startsWith("data:")) {
        if (!data.isEmpty()) {
          data.append('\n');
        }
        data.append(value(line, "data:"));
      }
    }

    @Override
    public void onError(final Throwable error) {
      ended(this, error);
    }

    @Override
    public void onComplete() {
      ended(this, null);
    }

    private void dispatch() {
      final var type = event;
      final var payload = data.toString();
      event = null;
      data.setLength(0);
      if (!"upload".equals(type) || payload.isEmpty()) {
        return;
      }
      try {
        onUpload.accept(UploadNotice.fromJson(payload));
      } catch (final IOException e) {
        LOG.debug("Skipping an upload event that isn't a notice: {}", payload, e);
      }
    }

    private static String value(final String line, final String field) {
      final var value = line.substring(field.length());
      return value.startsWith(" ") ? value.substring(1) : value;
    }
  }
}

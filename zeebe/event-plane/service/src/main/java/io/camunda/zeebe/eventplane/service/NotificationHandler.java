/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpExchange;
import io.camunda.zeebe.eventplane.notification.UploadNotice;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /v1/notifications?log={c}/{t}/{p}&log=…}: a stream of server-sent events, each {@code
 * event: upload} with the notice's JSON as its {@code data}, for the notices of the logs. An idle
 * stream gets the comment {@code : keep-alive} every keep-alive period. The stream belongs to the
 * thread of the request, which writes until a write fails, because the client went away, or the
 * service closes.
 */
final class NotificationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationHandler.class);
  private static final byte[] KEEP_ALIVE = ": keep-alive\n\n".getBytes(StandardCharsets.UTF_8);

  private final NoticeBoard notices;
  private final Duration keepAlive;

  NotificationHandler(final NoticeBoard notices, final Duration keepAlive) {
    this.notices = notices;
    this.keepAlive = keepAlive;
  }

  void handle(final HttpExchange exchange) throws IOException {
    final var logs = logs(exchange.getRequestURI().getRawQuery());
    // Subscribe before the response starts, so that a client that has its headers misses nothing.
    final var subscription = notices.subscribe(logs);
    try {
      exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
      exchange.getResponseHeaders().set("Cache-Control", "no-cache");
      exchange.sendResponseHeaders(200, 0);
      stream(subscription, exchange.getResponseBody());
    } catch (final IOException e) {
      LOG.debug(
          "Stopped a notification stream of {}, as its client is gone: {}", logs, e.toString());
    } finally {
      notices.unsubscribe(subscription);
    }
  }

  private void stream(final Subscription subscription, final OutputStream out) throws IOException {
    // Sends the headers now: the server holds them back until the first flush of the body.
    out.flush();
    while (true) {
      final UploadNotice notice;
      try {
        notice = subscription.poll(keepAlive);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      if (notice == null && subscription.isClosed()) {
        return;
      }
      out.write(notice == null ? KEEP_ALIVE : event(notice));
      out.flush();
    }
  }

  private static byte[] event(final UploadNotice notice) {
    return ("event: upload\ndata: " + new String(notice.toJson(), StandardCharsets.UTF_8) + "\n\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * The logs of the {@code log} parameters of a query.
   *
   * @throws InvalidRequestException if there is none, or one isn't {@code {c}/{t}/{p}}
   */
  private static Set<LogId> logs(@Nullable final String rawQuery) {
    final var logs = new LinkedHashSet<LogId>();
    if (rawQuery != null) {
      for (final var parameter : rawQuery.split("&")) {
        final int equals = parameter.indexOf('=');
        if (equals > 0 && decode(parameter.substring(0, equals)).equals("log")) {
          logs.add(LogId.parse(decode(parameter.substring(equals + 1))));
        }
      }
    }
    if (logs.isEmpty()) {
      throw new InvalidRequestException(
          "at least one parameter log={cluster}/{tenant}/{partition}", "none");
    }
    return logs;
  }

  private static String decode(final String encoded) {
    try {
      return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    } catch (final IllegalArgumentException e) {
      throw new InvalidRequestException("a query in percent-encoding", "'" + encoded + "'");
    }
  }
}

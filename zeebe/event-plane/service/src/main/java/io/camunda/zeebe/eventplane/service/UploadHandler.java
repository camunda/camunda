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

/**
 * {@code POST /v1/uploads}: takes an upload notice from a publisher, or from a peer. It remembers
 * the notice, passes it on to the notification streams of its log, and to the peers, unless a peer
 * sent it. It answers 202, as nothing it does can be waited for.
 */
final class UploadHandler {

  private static final int MAX_BODY_BYTES = 64 * 1024;

  private final NoticeBoard notices;
  private final PeerForwarder forwarder;

  UploadHandler(final NoticeBoard notices, final PeerForwarder forwarder) {
    this.notices = notices;
    this.forwarder = forwarder;
  }

  void handle(final HttpExchange exchange) throws IOException {
    final var notice = readNotice(exchange);
    notices.publish(notice);
    if (!"true"
        .equalsIgnoreCase(exchange.getRequestHeaders().getFirst(PeerForwarder.FORWARDED_HEADER))) {
      forwarder.forward(notice);
    }
    Responses.empty(exchange, 202);
  }

  private static UploadNotice readNotice(final HttpExchange exchange) throws IOException {
    final var body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw new InvalidRequestException(
          "an upload notice of at most %d bytes".formatted(MAX_BODY_BYTES), "more");
    }
    try {
      return UploadNotice.fromJson(body);
    } catch (final IOException | IllegalArgumentException e) {
      throw new InvalidRequestException(
          "an upload notice as JSON",
          String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
    }
  }
}

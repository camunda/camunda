/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** A peer that only records the requests it gets, and answers 202. */
final class StubPeer implements AutoCloseable {

  private final HttpServer server;
  private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

  StubPeer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          final var body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          requests.add(
              new Request(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst(PeerForwarder.FORWARDED_HEADER),
                  body));
          exchange.sendResponseHeaders(202, -1);
          exchange.close();
        });
    server.start();
  }

  URI uri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  /** The next request, or null if none comes in time. */
  @Nullable Request next(final Duration timeout) throws InterruptedException {
    return requests.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  /** What the peer got. */
  record Request(String method, String path, @Nullable String forwarded, String body) {}
}

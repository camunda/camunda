/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Event Plane's stateless service, on the JDK's HTTP server:
 *
 * <ul>
 *   <li>{@code POST /v1/uploads}: an upload notice from a publisher. It goes to the notification
 *       streams of its log, and to the peers.
 *   <li>{@code GET /v1/notifications?log={c}/{t}/{p}&log=…}: server-sent events with the notices of
 *       those logs.
 *   <li>{@code GET /v1/groups/{group}}: the workers, cursors and lag of a consumer group, read from
 *       the store.
 *   <li>{@code GET /v1/clusters}: the stores that the registered clusters write to, secrets masked.
 *   <li>{@code GET /v1/credentials}: a stub, the configured JSON, or with {@code ?cluster=c} the
 *       store of that cluster, secret included.
 * </ul>
 *
 * It keeps no state that matters. A restart loses the newest notice it remembered of each log, and
 * the open streams, whose workers reconnect; the store has all the rest. The caller owns the store
 * and closes it after the service.
 */
public final class EventPlaneService implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(EventPlaneService.class);
  private static final long SHUTDOWN_SECONDS = 2;

  private final ServiceConfig config;
  private final NoticeBoard notices = new NoticeBoard();
  private final PeerForwarder forwarder;
  private final Router router;

  private @Nullable HttpServer server;
  private @Nullable ExecutorService executor;
  private boolean closed;

  public EventPlaneService(final ObjectStore store, final ServiceConfig config) {
    this.config = config;
    forwarder = new PeerForwarder(config.peers());
    router =
        new Router(
            new UploadHandler(notices, forwarder),
            new NotificationHandler(notices, config.keepAlive()),
            new GroupHandler(new GroupView(store, notices)),
            new ClusterHandler(
                config.clusters(), config.credentialsJson().getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Binds the configured address and starts answering requests.
   *
   * @throws IOException if the address can't be bound
   * @throws IllegalStateException if the service is started or closed already
   */
  public synchronized void start() throws IOException {
    if (closed || server != null) {
      throw new IllegalStateException(
          "Expected the service to be new, but it is " + (closed ? "closed" : "started"));
    }
    final var created = HttpServer.create(config.address(), 0);
    // A thread for each request, and so for each notification stream, which holds it open.
    executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("event-plane-service-", 0).factory());
    created.setExecutor(executor);
    created.createContext("/", router);
    created.start();
    server = created;
    LOG.info("The Event Plane service listens on {}", created.getAddress());
  }

  /**
   * The port that the service listens on, which is the configured one unless that was 0.
   *
   * @throws IllegalStateException if the service isn't started
   */
  public synchronized int port() {
    if (server == null) {
      throw new IllegalStateException("Expected the service to be started, but it isn't");
    }
    return server.getAddress().getPort();
  }

  /** How many notification streams are open. */
  public int openStreams() {
    return notices.subscribers();
  }

  /** Ends the open streams, stops answering requests, and frees the port. Does nothing twice. */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    notices.close();
    if (server != null) {
      server.stop(0);
    }
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (final InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    forwarder.close();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/** A running service on a free port, an HTTP client for it, and the streams that a test opened. */
final class ServiceFixture implements AutoCloseable {

  private final HttpClient client =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private final List<SseStream> streams = new CopyOnWriteArrayList<>();
  private final EventPlaneService service;

  private ServiceFixture(final EventPlaneService service) {
    this.service = service;
  }

  /** Starts a service on a free port of this machine, with the default configuration. */
  static ServiceFixture start(final ObjectStore store) throws IOException {
    return start(store, config -> config);
  }

  /** Starts a service on a free port of this machine, with the configuration that is changed. */
  static ServiceFixture start(final ObjectStore store, final UnaryOperator<ServiceConfig> configure)
      throws IOException {
    return start(store, 0, configure);
  }

  /** Starts a service on the port of this machine, with the configuration that is changed. */
  static ServiceFixture start(
      final ObjectStore store, final int port, final UnaryOperator<ServiceConfig> configure)
      throws IOException {
    final var config = configure.apply(new ServiceConfig(new InetSocketAddress("127.0.0.1", port)));
    final var service = new EventPlaneService(store, config);
    service.start();
    return new ServiceFixture(service);
  }

  EventPlaneService service() {
    return service;
  }

  /** The URL of a path, with its query if it has one. */
  URI uri(final String pathAndQuery) {
    return URI.create("http://127.0.0.1:" + service.port() + pathAndQuery);
  }

  HttpResponse<String> get(final String path) throws Exception {
    return send("GET", path, null);
  }

  HttpResponse<String> post(final String path, final String body, final String... headers)
      throws Exception {
    return send("POST", path, body, headers);
  }

  /** Posts the notice, as a publisher does, and checks that the service accepted it. */
  void post(final UploadNotice notice) throws Exception {
    final var response = post("/v1/uploads", new String(notice.toJson(), StandardCharsets.UTF_8));
    assertThat(response.statusCode()).isEqualTo(202);
  }

  /** A request of any method; the headers are names and values, one after the other. */
  HttpResponse<String> send(
      final String method, final String path, @Nullable final String body, final String... headers)
      throws Exception {
    final var request =
        HttpRequest.newBuilder(uri(path))
            .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    return client.send(request.build(), BodyHandlers.ofString());
  }

  /** Opens a notification stream of the logs, each as {@code {cluster}/{tenant}/{partition}}. */
  SseStream stream(final String... logs) throws Exception {
    final var query = new StringBuilder();
    for (final var log : logs) {
      query.append(query.isEmpty() ? "?" : "&").append("log=").append(log);
    }
    final var stream = SseStream.open(client, uri("/v1/notifications" + query));
    streams.add(stream);
    return stream;
  }

  @Override
  public void close() throws IOException {
    // The service ends the streams, which the clients can't close while they wait for a line.
    service.close();
    for (final var stream : streams) {
      stream.close();
    }
    client.shutdownNow();
  }
}

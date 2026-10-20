/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Passes an upload notice on to the other instances of the service, so that the workers that are
 * connected to any of them hear of it. It is asynchronous and best effort: a peer that is down
 * misses the notice, and its workers find the segment by listing. The request carries {@value
 * #FORWARDED_HEADER}, so that the peer doesn't pass it on again.
 */
final class PeerForwarder implements AutoCloseable {

  /** The header that marks a notice that a peer passed on, which isn't passed on again. */
  static final String FORWARDED_HEADER = "X-Event-Plane-Forwarded";

  private static final Logger LOG = LoggerFactory.getLogger(PeerForwarder.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final List<URI> uploadEndpoints;
  private final HttpClient client;

  PeerForwarder(final List<URI> peers) {
    uploadEndpoints = peers.stream().map(PeerForwarder::uploadEndpoint).toList();
    client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
  }

  /** Sends the notice to every peer, without waiting for the answers. */
  void forward(final UploadNotice notice) {
    if (uploadEndpoints.isEmpty()) {
      return;
    }
    final var json = notice.toJson();
    for (final var endpoint : uploadEndpoints) {
      try {
        final var request =
            HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header(FORWARDED_HEADER, "true")
                .POST(BodyPublishers.ofByteArray(json))
                .build();
        client
            .sendAsync(request, BodyHandlers.discarding())
            .whenComplete(
                (response, error) ->
                    report(endpoint, response == null ? 0 : response.statusCode(), error));
      } catch (final RuntimeException e) {
        // The client is shut down, or the request is invalid: nothing to wait for.
        LOG.debug("Expected to pass a notice on to {}, but got {}", endpoint, e.toString());
      }
    }
  }

  @Override
  public void close() {
    client.shutdownNow();
  }

  private static void report(final URI endpoint, final int status, final Throwable error) {
    if (error != null) {
      LOG.warn("Expected the peer {} to accept a notice, but got {}", endpoint, error.toString());
    } else if (status != 202) {
      LOG.warn("Expected the peer {} to answer 202 to a notice, but got {}", endpoint, status);
    }
  }

  private static URI uploadEndpoint(final URI peer) {
    final var base = peer.toString();
    return URI.create(
        (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/v1/uploads");
  }
}

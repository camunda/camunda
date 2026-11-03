/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A physical tenant's Elasticsearch transport that opens its HTTP client, with its I/O threads and
 * connections, on the first request, and holds it for as long as a request runs, so that it can be
 * released while no request does.
 */
@NullMarked
final class LazyElasticsearchTransport implements ElasticsearchTransport {

  private final LazyPhysicalTenantResource<ElasticsearchTransport> transport;

  LazyElasticsearchTransport(final LazyPhysicalTenantResource<ElasticsearchTransport> transport) {
    this.transport = transport;
  }

  @Override
  public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
      final RequestT request,
      final Endpoint<RequestT, ResponseT, ErrorT> endpoint,
      final @Nullable TransportOptions options)
      throws IOException {
    try (final var lease = transport.acquire()) {
      return lease.resource().performRequest(request, endpoint, options);
    }
  }

  @Override
  public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
      final RequestT request,
      final Endpoint<RequestT, ResponseT, ErrorT> endpoint,
      final @Nullable TransportOptions options) {
    final var lease = transport.acquire();
    try {
      return lease
          .resource()
          .performRequestAsync(request, endpoint, options)
          .whenComplete((response, error) -> lease.close());
    } catch (final RuntimeException e) {
      lease.close();
      throw e;
    }
  }

  @Override
  public JsonpMapper jsonpMapper() {
    return transport.use(ElasticsearchTransport::jsonpMapper);
  }

  @Override
  public TransportOptions options() {
    return transport.use(ElasticsearchTransport::options);
  }

  @Override
  public void close() {
    transport.close();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import io.camunda.application.commons.pt.IdlePhysicalTenantResourceReleaser;
import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.connect.tenant.SearchClients;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The search clients of every physical tenant on this node. Each client is a facade whose HTTP
 * client, with its I/O threads and connections, is opened on the first request and released after
 * the tenant's idle release timeout without a request. Whoever holds a client keeps a working one.
 */
@NullMarked
public final class PhysicalTenantSearchClientResources implements AutoCloseable {

  private static final Logger LOG =
      LoggerFactory.getLogger(PhysicalTenantSearchClientResources.class);
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

  private final SearchClients searchClients;
  private final Map<String, List<LazyPhysicalTenantResource<? extends Closeable>>> byTenant;
  private final IdlePhysicalTenantResourceReleaser releaser;

  private PhysicalTenantSearchClientResources(
      final SearchClients searchClients,
      final Map<String, List<LazyPhysicalTenantResource<? extends Closeable>>> byTenant,
      final IdlePhysicalTenantResourceReleaser releaser) {
    this.searchClients = searchClients;
    this.byTenant = byTenant;
    this.releaser = releaser;
  }

  public static PhysicalTenantSearchClientResources create(
      final Map<String, ConnectConfiguration> configs,
      final Function<String, Duration> idleReleaseTimeoutOf) {
    final var esClients = new LinkedHashMap<String, ElasticsearchClient>();
    final var osClients = new LinkedHashMap<String, OpenSearchClient>();
    final var osAsyncClients = new LinkedHashMap<String, OpenSearchAsyncClient>();
    final var byTenant =
        new LinkedHashMap<String, List<LazyPhysicalTenantResource<? extends Closeable>>>();
    final var releasable = new ArrayList<IdlePhysicalTenantResourceReleaser.Entry>();

    configs.forEach(
        (tenantId, config) -> {
          final var type = config.getTypeEnum();
          final List<LazyPhysicalTenantResource<? extends Closeable>> resources;
          if (type.isElasticSearch()) {
            final var connector = new ElasticsearchConnector(config);
            final var transport =
                lazy(
                    tenantId,
                    "Elasticsearch client",
                    () -> (ElasticsearchTransport) connector.createClient()._transport());
            esClients.put(
                tenantId, new ElasticsearchClient(new LazyElasticsearchTransport(transport)));
            resources = List.of(transport);
          } else if (type.isOpenSearch()) {
            final var connector = new OpensearchConnector(config);
            final var sync =
                lazy(tenantId, "OpenSearch client", () -> connector.createClient()._transport());
            final var async =
                lazy(
                    tenantId,
                    "OpenSearch async client",
                    () -> connector.createAsyncClient()._transport());
            osClients.put(tenantId, new OpenSearchClient(new LazyOpenSearchTransport(sync)));
            osAsyncClients.put(
                tenantId, new OpenSearchAsyncClient(new LazyOpenSearchTransport(async)));
            resources = List.of(sync, async);
          } else {
            throw new SearchClientConnectException(
                "Physical tenant secondary storage currently only supports ElasticSearch or OpenSearch");
          }
          byTenant.put(tenantId, resources);
          final var idleReleaseTimeout = idleReleaseTimeoutOf.apply(tenantId);
          resources.forEach(
              r ->
                  releasable.add(
                      new IdlePhysicalTenantResourceReleaser.Entry(r, idleReleaseTimeout)));
        });

    return new PhysicalTenantSearchClientResources(
        new SearchClients(esClients, osClients, osAsyncClients),
        Map.copyOf(byTenant),
        IdlePhysicalTenantResourceReleaser.start("search", releasable));
  }

  public SearchClients searchClients() {
    return searchClients;
  }

  /** Whether this node has opened any search client of the tenant. */
  public boolean isCreated(final String physicalTenantId) {
    return byTenant.getOrDefault(physicalTenantId, List.of()).stream()
        .anyMatch(r -> r.state() == LazyPhysicalTenantResource.State.CREATED);
  }

  /**
   * Runs background work, such as a health check, on the tenant's first client only while it is
   * open, without opening it and without counting as a use.
   */
  public <R> Optional<R> whileCreated(final String physicalTenantId, final Supplier<R> work) {
    final var resources = byTenant.get(physicalTenantId);
    if (resources == null || resources.isEmpty()) {
      return Optional.empty();
    }
    final var lease = resources.getFirst().acquireIfCreated();
    if (lease.isEmpty()) {
      return Optional.empty();
    }
    try (final var held = lease.get()) {
      return Optional.ofNullable(work.get());
    }
  }

  @Override
  public void close() {
    releaser.close();
    byTenant.values().stream().flatMap(List::stream).forEach(LazyPhysicalTenantResource::close);
  }

  private static <T extends Closeable> LazyPhysicalTenantResource<T> lazy(
      final String tenantId, final String name, final Supplier<T> factory) {
    return new LazyPhysicalTenantResource<>(
        tenantId,
        name,
        factory,
        PhysicalTenantSearchClientResources::closeQuietly,
        RETRY_BACKOFF,
        InstantSource.system());
  }

  private static void closeQuietly(final Closeable transport) {
    try {
      transport.close();
    } catch (final IOException | RuntimeException e) {
      LOG.debug("Failed to close a search client transport", e);
    }
  }
}

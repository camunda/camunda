/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.test.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * {@code ContainerizedSearchDBExtension} is an extension that creates and manages a containerized
 * Elasticsearch/OpenSearch instance, creates and configures the respective client, and provides a
 * client for interaction for usage in tests.
 *
 * <p>This extension will create both ElasticSearch and OpenSearch containers at the same time, so
 * tests may be executed against both types of databases.
 */
public class ContainerizedSearchDBExtension extends SearchDBExtension {
  private final LazySearchContainer<ElasticsearchContainer, ElasticsearchClient> elasticsearch =
      new LazySearchContainer<>(
          TestSearchContainers::createDefeaultElasticsearchContainer,
          ContainerizedSearchDBExtension::createElasticSearchClient,
          ElasticsearchClient::close);
  private final LazySearchContainer<OpenSearchContainer<?>, OpenSearchClient> opensearch =
      new LazySearchContainer<>(
          TestSearchContainers::createDefaultOpensearchContainer,
          ContainerizedSearchDBExtension::createOpenSearchClient,
          osClient -> osClient._transport().close());

  private final AtomicInteger refCount = new AtomicInteger();
  private final ObjectMapper objectMapper =
      new JacksonConfiguration(new ConnectConfiguration()).createObjectMapper();

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    refCount.getAndIncrement();
  }

  private static ElasticsearchClient createElasticSearchClient(
      final ElasticsearchContainer container) {
    final var config = new ConnectConfiguration();
    config.setUrl(container.getHttpHostAddress());
    final var esConnector = new ElasticsearchConnector(config);
    return esConnector.createClient();
  }

  private static OpenSearchClient createOpenSearchClient(final OpenSearchContainer<?> container) {
    final var osConfig = new ConnectConfiguration();
    osConfig.setType(DatabaseConfig.OPENSEARCH);
    osConfig.setUrl(container.getHttpHostAddress());
    final var osConnector = new OpensearchConnector(osConfig);
    return osConnector.createClient();
  }

  /** {@inheritDoc} */
  @Override
  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  /** {@inheritDoc} */
  @Override
  public ElasticsearchClient esClient() {
    return elasticsearch.getClient();
  }

  /** {@inheritDoc} */
  @Override
  public OpenSearchClient osClient() {
    return opensearch.getClient();
  }

  /** {@inheritDoc} */
  @Override
  public String esUrl() {
    return elasticsearch.getContainer().getHttpHostAddress();
  }

  /** {@inheritDoc} */
  @Override
  public String osUrl() {
    return opensearch.getContainer().getHttpHostAddress();
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    // if we are using @Nested then beforeAll/afterAll will be called multiple times, so we need to
    // make sure to only close the containers when the last afterAll is called
    if (refCount.decrementAndGet() == 0) {
      elasticsearch.close();
      opensearch.close();
    }
  }

  record ContainerAndClient<T extends GenericContainer<?>, C>(T container, C client) {}

  static class LazySearchContainer<T extends GenericContainer<?>, C> implements AutoCloseable {
    private final AtomicReference<ContainerAndClient<T, C>> containerAndClient =
        new AtomicReference<>();
    private final Supplier<ContainerAndClient<T, C>> containerAndClientCreator;
    private final ClientCloser<C> clientCloser;

    public LazySearchContainer(
        final Supplier<T> containerSupplier,
        final Function<T, C> clientCreator,
        final ClientCloser<C> clientCloser) {
      // using the memoize supplier to ensure we only create one container instance
      containerAndClientCreator =
          Suppliers.memoize(
              () -> {
                final var container = containerSupplier.get();
                container.start();
                final var client = clientCreator.apply(container);
                return new ContainerAndClient<>(container, client);
              });
      this.clientCloser = clientCloser;
    }

    @Override
    public void close() throws IOException {
      final var containerAndClient = this.containerAndClient.get();
      if (containerAndClient != null) {
        try {
          clientCloser.close(containerAndClient.client());
        } finally {
          containerAndClient.container().stop();
        }
      }
    }

    public T getContainer() {
      return getContainerAndClient().container();
    }

    public C getClient() {
      return getContainerAndClient().client();
    }

    private ContainerAndClient<T, C> getContainerAndClient() {
      return containerAndClient.updateAndGet(previous -> containerAndClientCreator.get());
    }
  }

  interface ClientCloser<C> {
    void close(C client) throws IOException;
  }
}

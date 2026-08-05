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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
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
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ContainerizedSearchDBExtension.class);
  private final LazySearchContainer<ElasticsearchContainer, ElasticsearchClient> elasticsearch =
      new LazySearchContainer<>(
          TestSearchContainers::createDefaultElasticsearchContainer,
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

  /** {@inheritDoc} */
  @Override
  public Duration dataAvailabilityTimeout() {
    return Duration.ofSeconds(30);
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
    private final ReentrantLock lock = new ReentrantLock();
    private final Supplier<T> containerSupplier;
    private final Function<T, C> clientCreator;
    private final ClientCloser<C> clientCloser;
    private ContainerAndClient<T, C> containerAndClient;

    public LazySearchContainer(
        final Supplier<T> containerSupplier,
        final Function<T, C> clientCreator,
        final ClientCloser<C> clientCloser) {
      this.containerSupplier = containerSupplier;
      this.clientCreator = clientCreator;
      this.clientCloser = clientCloser;
    }

    @Override
    public void close() throws IOException {
      lock.lock();
      try {
        if (containerAndClient != null) {
          try {
            clientCloser.close(containerAndClient.client());
          } finally {
            containerAndClient.container().stop();
          }
        }
      } finally {
        containerAndClient = null;
        lock.unlock();
      }
    }

    public T getContainer() {
      return getContainerAndClient().container();
    }

    public C getClient() {
      return getContainerAndClient().client();
    }

    private ContainerAndClient<T, C> getContainerAndClient() {
      lock.lock();
      try {
        if (containerAndClient == null) {
          final var container = containerSupplier.get();
          final StartupLogRecorder logRecorder = new StartupLogRecorder();
          container.withLogConsumer(logRecorder);
          boolean startSucceeded = false;
          try {
            container.start();
            startSucceeded = true;
          } finally {
            logRecorder.stopRecording();
            if (!startSucceeded) {
              LOGGER.error("Search container failed to start. Logs from the container:");
              logRecorder.outputLogs(LOGGER);
            }
            logRecorder.clearLogs();
          }
          try {
            final var client = clientCreator.apply(container);
            containerAndClient = new ContainerAndClient<>(container, client);
          } catch (final Exception e) {
            container.stop();
            throw new RuntimeException("Failed to create client for search container", e);
          }
        }
        return containerAndClient;
      } finally {
        lock.unlock();
      }
    }
  }

  static class StartupLogRecorder implements Consumer<OutputFrame> {
    private static final int MAX_LOG_LINES = 1000;
    private final Queue<String> logs = new ConcurrentLinkedQueue<>();
    private volatile boolean recording = true;

    void stopRecording() {
      recording = false;
    }

    @Override
    public void accept(final OutputFrame outputFrame) {
      if (!recording) {
        return;
      }
      final var logLine = outputFrame.getUtf8String().trim();
      if (!logLine.isEmpty()) {
        logs.add(logLine);
      }

      while (logs.size() > MAX_LOG_LINES) {
        if (logs.poll() == null) {
          break;
        }
      }
    }

    void outputLogs(final Logger logger) {
      for (final String log : logs) {
        logger.error(log);
      }
    }

    void clearLogs() {
      logs.clear();
    }
  }

  interface ClientCloser<C> {
    void close(C client) throws IOException;
  }
}

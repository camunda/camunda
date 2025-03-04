/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.testcontainers.OpensearchContainer;

public class ContainerizedSearchDBExtension extends SearchDBExtension {

  private static final String PASSWORD = "P@a$5w0rd";
  private static final String ADMIN_PASSWORD_ENV_VAR = "OPENSEARCH_INITIAL_ADMIN_PASSWORD";

  private static OpensearchContainer<?> opensearchContainer;

  private ProtocolFactory recordFactory;
  private OpensearchExporterConfiguration config;
  private TemplateReader templateReader;
  private RecordIndexRouter indexRouter;
  private BulkIndexRequest bulkRequest;

  private TestClient testClient;
  private OpensearchClient client;

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    opensearchContainer =
        TestSearchContainers.createDefaultOpensearchContainer()
            .withSecurityEnabled()
            .withEnv(ADMIN_PASSWORD_ENV_VAR, PASSWORD);
    opensearchContainer.start();
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    recordFactory = new ProtocolFactory();
    config = new OpensearchExporterConfiguration();
    templateReader = new TemplateReader(config.index);
    indexRouter = new RecordIndexRouter(config.index);
    bulkRequest = new BulkIndexRequest();

    // as all tests use the same endpoint, we need a per-test unique prefix
    config.index.prefix = UUID.randomUUID() + "-test-record";
    config.url = opensearchContainer.getHttpHostAddress();
    config.getAuthentication().setUsername(opensearchContainer.getUsername());
    config.getAuthentication().setPassword(PASSWORD);
    testClient = new TestClient(config, indexRouter);
    client =
        new OpensearchClient(
            config,
            bulkRequest,
            RestClientFactory.of(config, true),
            indexRouter,
            templateReader,
            new OpensearchMetrics(new SimpleMeterRegistry()));
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    CloseHelper.quietCloseAll(testClient, client);
  }

  @Override
  public OpensearchExporterConfiguration config() {
    return config;
  }

  @Override
  public ProtocolFactory recordFactory() {
    return recordFactory;
  }

  @Override
  public TemplateReader templateReader() {
    return templateReader;
  }

  @Override
  public RecordIndexRouter indexRouter() {
    return indexRouter;
  }

  @Override
  public BulkIndexRequest bulkRequest() {
    return bulkRequest;
  }

  @Override
  public TestClient testClient() {
    return testClient;
  }

  @Override
  public OpensearchClient client() {
    return client;
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {}
}

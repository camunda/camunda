/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@code AWSSearchDBExtension} is an extension that manages an AWS-based OpenSearch instance,
 * creates and configures respective client, and provides a client for interaction for usage in
 * tests.
 *
 * <p>To use this extension, preconditions from {@link SearchDBExtension} must be met.
 *
 * <p>This extension fetches the AWS URL from the {@link
 * SearchDBExtension#TEST_INTEGRATION_OPENSEARCH_AWS_URL} argument.
 *
 * <p>This extension uses the {@link
 * software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider} for implicit authentication.
 */
public class AWSSearchDBExtension extends SearchDBExtension {

  private static final String PREFIX_AWS_OS_TESTS = "exportertests";

  private final String awsSearchDbUrl;

  private ProtocolFactory recordFactory;
  private OpensearchExporterConfiguration config;
  private TemplateReader templateReader;
  private RecordIndexRouter indexRouter;
  private BulkIndexRequest bulkRequest;

  private TestClient testClient;
  private OpensearchClient client;

  public AWSSearchDBExtension(final String awsSearchDbUrl) {
    this.awsSearchDbUrl = awsSearchDbUrl;
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    recordFactory = new ProtocolFactory();
    config = new OpensearchExporterConfiguration();
    config.aws.enabled = true;
    templateReader = new TemplateReader(config.index);
    indexRouter = new RecordIndexRouter(config.index);
    bulkRequest = new BulkIndexRequest();

    // as all tests use the same endpoint, we need a per-test unique prefix
    config.index.prefix = PREFIX_AWS_OS_TESTS + UUID.randomUUID() + "-test-record";
    config.url = awsSearchDbUrl;
    testClient = new TestClient(config, indexRouter);
    client =
        new OpensearchClient(
            config,
            bulkRequest,
            OpensearchConnector.of(config).createClient(),
            RestClientFactory.of(config, true),
            indexRouter,
            templateReader,
            new OpensearchMetrics(new SimpleMeterRegistry()));
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    // No-Op
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    // No-Op
  }

  /** {@inheritDoc} */
  @Override
  public OpensearchExporterConfiguration config() {
    return config;
  }

  /** {@inheritDoc} */
  @Override
  public ProtocolFactory recordFactory() {
    return recordFactory;
  }

  /** {@inheritDoc} */
  @Override
  public TemplateReader templateReader() {
    return templateReader;
  }

  /** {@inheritDoc} */
  @Override
  public RecordIndexRouter indexRouter() {
    return indexRouter;
  }

  /** {@inheritDoc} */
  @Override
  public BulkIndexRequest bulkRequest() {
    return bulkRequest;
  }

  /** {@inheritDoc} */
  @Override
  public TestClient testClient() {
    return testClient;
  }

  /** {@inheritDoc} */
  @Override
  public OpensearchClient client() {
    return client;
  }

  /** {@inheritDoc} */
  @Override
  public void afterAll(final ExtensionContext context) throws Exception {}
}

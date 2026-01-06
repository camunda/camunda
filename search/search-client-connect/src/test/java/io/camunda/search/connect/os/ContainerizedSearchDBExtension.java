/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;

/**
 * {@code ContainerizedSearchDBExtension} is an extension that creates and manages a containerized
 * test containers based OpenSearch instance, creates and configures respective client, and provides
 * a client for interaction for usage in tests.
 */
public class ContainerizedSearchDBExtension extends SearchDBExtension {

  private static OpensearchContainer opensearchContainer;
  private static OpenSearchClient osClient;
  private static OpenSearchAsyncClient asyncOsClient;

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    opensearchContainer = TestSearchContainers.createDefaultOpensearchContainer();
    opensearchContainer.start();

    final var configuration = new ConnectConfiguration();
    configuration.setUrl(opensearchContainer.getHttpHostAddress());
    final var connector = new OpensearchConnector(configuration);
    osClient = connector.createClient();
    asyncOsClient = connector.createAsyncClient();
  }

  /** {@inheritDoc} */
  @Override
  public OpenSearchClient osClient() {
    return osClient;
  }

  /** {@inheritDoc} */
  @Override
  public OpenSearchAsyncClient asyncOsClient() {
    return asyncOsClient;
  }

  /** {@inheritDoc} */
  @Override
  public String osUrl() {
    return opensearchContainer.getHttpHostAddress();
  }
}

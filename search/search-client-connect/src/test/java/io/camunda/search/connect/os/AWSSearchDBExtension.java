/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;

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

  private static OpenSearchClient osClient;

  private static OpenSearchAsyncClient asyncOsClient;

  private final String osUrl;

  public AWSSearchDBExtension(final String openSearchAwsInstanceUrl) {
    osUrl = openSearchAwsInstanceUrl;
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final var url = System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL);
    final var configuration = new ConnectConfiguration();
    configuration.setUrl(url);
    configuration.setAwsEnabled(true);
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
    return osUrl;
  }
}

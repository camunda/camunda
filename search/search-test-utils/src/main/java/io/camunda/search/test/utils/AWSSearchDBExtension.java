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
import io.camunda.search.connect.os.OpensearchConnector;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtensionContext;
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
 *
 * <p>This extension always returns `null` for all ElasticSearch related methods, meaning test
 * maintainer has to make sure it won't fail on a CI.
 */
public class AWSSearchDBExtension extends SearchDBExtension {

  private static OpenSearchClient osClient;

  private final String osUrl;
  private ObjectMapper objectMapper;

  public AWSSearchDBExtension(final String openSearchAwsInstanceUrl) {
    osUrl = openSearchAwsInstanceUrl;
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final var osConfig = new ConnectConfiguration();
    osConfig.setType("opensearch");
    osConfig.setUrl(osUrl);
    osConfig.setIndexPrefix("test-" + UUID.randomUUID());
    osConfig.setAwsEnabled(true);
    final var connector = new OpensearchConnector(osConfig);
    objectMapper = connector.objectMapper();
    osClient = connector.createClient();
  }

  /** {@inheritDoc} */
  @Override
  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  /** {@inheritDoc} */
  @Override
  public ElasticsearchClient esClient() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public OpenSearchClient osClient() {
    return osClient;
  }

  /** {@inheritDoc} */
  @Override
  public String esUrl() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String osUrl() {
    return osUrl;
  }

  @Override
  public boolean isAws() {
    return true;
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    osClient.indices().delete(req -> req.index(IDX_FORM_PREFIX + "*"));
    osClient.indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    osClient.indices().delete(req -> req.index(IDX_PROCESS_PREFIX + "*"));
    osClient.indices().delete(req -> req.index(ZEEBE_IDX_PREFIX + "*"));
    osClient.indices().delete(req -> req.index(ARCHIVER_IDX_PREFIX + "*"));
    osClient.indices().delete(req -> req.index(BATCH_IDX_PREFIX + "*"));
    osClient.indices().delete(req -> req.index(INCIDENT_IDX_PREFIX + "*"));
    osClient.indices().delete(req -> req.index("*" + ENGINE_CLIENT_TEST_MARKERS + "*"));
    osClient._transport().close();
  }
}

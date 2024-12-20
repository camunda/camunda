/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import static io.camunda.exporter.config.ConnectionTypes.OPENSEARCH;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;

public class AWSOpenSearchDatabaseCallbackDelegate implements SearchDatabaseCallbackDelegate {

  protected static final String IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY =
      "camunda.it.opensearch.aws.url";

  protected SearchClientAdapter awsOsClientAdapter;
  private OpenSearchClient osClient;

  @Override
  public void afterAll(final ExtensionContext context) throws IOException {}

  @Override
  public void afterEach(final ExtensionContext context) throws IOException {
    if (context.getDisplayName().equals(OPENSEARCH.getType())) {
      osClient.indices().delete(req -> req.index("*"));
      osClient.indices().deleteIndexTemplate(req -> req.name("*"));
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws IOException {
    final var osConfig = getConfigWithConnectionDetails(OPENSEARCH);
    osClient = new OpensearchConnector(osConfig.getConnect()).createClient();
    awsOsClientAdapter = new SearchClientAdapter(osClient);
  }

  @Override
  public ExporterConfiguration getConfigWithConnectionDetails(
      final ConnectionTypes connectionType) {
    final var config = new ExporterConfiguration();
    config.getIndex().setPrefix(CamundaExporterITInvocationProvider.CONFIG_PREFIX);
    config.getBulk().setSize(1); // force flushing on the first record

    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY))
            .orElseThrow();
    config.getConnect().setUrl(openSearchAwsInstanceUrl);

    config.getConnect().setIndexPrefix(CamundaExporterITInvocationProvider.CONFIG_PREFIX);
    config.getConnect().setType(OPENSEARCH.getType());
    return config;
  }

  // - name: Configure AWS Credentials for China region audience
  //      uses: aws-actions/configure-aws-credentials@v4
  //      with:
  //        audience: sts.amazonaws.com.cn
  //        aws-region: us-east-3
  //        role-to-assume: arn:aws-cn:iam::123456789100:role/my-github-actions-role

  @Override
  public Map<ConnectionTypes, SearchClientAdapter> contextAdapterRegistration() {
    return Map.of(OPENSEARCH, awsOsClientAdapter);
  }
}

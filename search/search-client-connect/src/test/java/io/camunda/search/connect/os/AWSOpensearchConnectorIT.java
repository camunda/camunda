/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.junit.jupiter.api.Test;

public class AWSOpensearchConnectorIT {
  protected static final String IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY =
      "camunda.it.opensearch.aws.url";

  @Test
  void shouldCreateConnectionToAWSOpenSearchAndRetrieveData() throws Exception {
    final var url = System.getProperty(IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY);
    final var configuration = new ConnectConfiguration();
    configuration.setUrl(url);
    final var connector = new OpensearchConnector(configuration);
    final var client = connector.createClient();

    final var count = client.count();
    assertThat(count).isNotEqualTo(0);
  }

  @Test
  void shouldCreateConnectionToAWSOpenSearchAndRetrieveDataAsyncClient() throws Exception {
    final var url = System.getProperty(IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY);
    final var configuration = new ConnectConfiguration();
    configuration.setUrl(url);
    final var connector = new OpensearchConnector(configuration);
    final var asyncClient = connector.createAsyncClient();

    final var count = asyncClient.count();
    assertThat(count).isNotEqualTo(0);
  }
}

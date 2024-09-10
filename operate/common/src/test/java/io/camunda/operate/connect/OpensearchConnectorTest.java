/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;

@ExtendWith(MockitoExtension.class)
public class OpensearchConnectorTest {

  private static final String AWS_REGION = "eu-west-1";
  private static final String AWS_SECRET_ACCESS_KEY = "awsSecretAcessKey";
  private static final String AWS_ACCESS_KEY_ID = "awsAccessKeyId";

  ObjectMapper objectMapper = new ObjectMapper();

  private final OperateOpensearchProperties opensearchProperties =
      new OperateOpensearchProperties();

  private final OperateProperties operateProperties = new OperateProperties();

  private OpensearchConnector opensearchConnector;

  @BeforeEach
  public void setup() {
    operateProperties.setOpensearch(opensearchProperties);
    opensearchConnector = new OpensearchConnector(operateProperties, objectMapper);
  }

  @Test
  public void asyncHasAwsEnabledAndAwsCredentialsSetAndShouldUseAwsCredentials() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    opensearchProperties.setAwsEnabled(true);

    final OpenSearchAsyncClient client =
        opensearchConnector.createAsyncOsClient(opensearchProperties);

    assertEquals(AwsSdk2Transport.class, client._transport().getClass());
  }

  @Test
  public void asyncHasAwsCredentialsButShouldUseBasicAuth() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    // awsEnabled not set -> should default to false
    final OpensearchConnector spyConnector = spy(opensearchConnector);
    doReturn(true).when(spyConnector).checkHealth(any(OpenSearchAsyncClient.class));

    final OpenSearchAsyncClient client = spyConnector.createAsyncOsClient(opensearchProperties);

    assertEquals(ApacheHttpClient5Transport.class, client._transport().getClass());
  }

  @Test
  public void syncHasAwsEnabledAndAwsCredentialsSetAndShouldUseAwsCredentials() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    opensearchProperties.setAwsEnabled(true);

    final OpenSearchClient client = opensearchConnector.createOsClient(opensearchProperties);

    assertEquals(AwsSdk2Transport.class, client._transport().getClass());
  }

  @Test
  public void syncHasAwsCredentialsButShouldUseBasicAuth() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    // awsEnabled not set -> should default to false
    final OpensearchConnector spyConnector = spy(opensearchConnector);
    doReturn(true).when(spyConnector).checkHealth(any(OpenSearchClient.class));

    final OpenSearchClient client = spyConnector.createOsClient(opensearchProperties);

    assertEquals(ApacheHttpClient5Transport.class, client._transport().getClass());
  }
}

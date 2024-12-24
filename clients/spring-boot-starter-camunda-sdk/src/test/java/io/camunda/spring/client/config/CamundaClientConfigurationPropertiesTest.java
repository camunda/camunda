/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static io.camunda.spring.client.properties.CamundaClientConfigurationProperties.CONNECTION_MODE_ADDRESS;
import static io.camunda.spring.client.properties.CamundaClientConfigurationProperties.CONNECTION_MODE_CLOUD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.spring.client.properties.CamundaClientConfigurationProperties;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties.Broker;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties.Cloud;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CamundaClientConfigurationPropertiesTest {

  private static final String BROKER_GRPC_ADDRESS =
      "https://cluster-id.cluster-region.base-url:123";
  private CamundaClientConfigurationProperties properties;

  @BeforeEach
  public void setUp() {
    properties = new CamundaClientConfigurationProperties(null);
  }

  @Test
  public void shouldUseCloudGrpcAddressWhenConnectionModeIsCloud() throws URISyntaxException {
    properties.setConnectionMode(CONNECTION_MODE_CLOUD);
    setCloudProperties();
    setBrokerProperties();

    assertThat(properties.getGrpcAddress())
        .isEqualTo(new URI("https://cluster-id.cluster-region.base-url:123"));
  }

  @Test
  public void shouldUseBrokerGrpcAddressWhenConnectionModeIsAddress() throws URISyntaxException {
    properties.setConnectionMode(CONNECTION_MODE_ADDRESS);
    setCloudProperties();
    setBrokerProperties();

    assertThat(properties.getGrpcAddress()).isEqualTo(new URI(BROKER_GRPC_ADDRESS));
  }

  @Test
  public void shouldThrowErrorWhenConnectionModeIsInvalid() throws URISyntaxException {
    properties.setConnectionMode("CUSTOM");

    final RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> properties.getGrpcAddress(),
            "Expected getGrpcAddress() to throw, but it didn't");

    assertTrue(exception.getMessage().contains("for ConnectionMode is invalid"));
  }

  @Test
  public void shouldUseCloudGrpcAddressFirstWhenNoConnectionMode() throws URISyntaxException {
    setCloudProperties();
    setBrokerProperties();

    assertThat(properties.getGrpcAddress())
        .isEqualTo(new URI("https://cluster-id.cluster-region.base-url:123"));
  }

  @Test
  public void shouldUseBrokerGrpcAddressWhenOnlyBrokerConfigAvailable() throws URISyntaxException {
    setBrokerProperties();

    assertThat(properties.getGrpcAddress()).isEqualTo(new URI(BROKER_GRPC_ADDRESS));
  }

  private void setCloudProperties() {
    final Cloud cloud = new Cloud();
    cloud.setClusterId("cluster-id");
    cloud.setRegion("cluster-region");
    cloud.setBaseUrl("base-url");
    cloud.setPort(123);
    properties.setCloud(cloud);
  }

  private void setBrokerProperties() throws URISyntaxException {
    final Broker broker = new Broker();
    final URI grpcAddress = new URI(BROKER_GRPC_ADDRESS);
    broker.setGrpcAddress(grpcAddress);
    properties.setBroker(broker);
  }
}

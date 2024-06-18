/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.spring.client.config;

import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.CONNECTION_MODE_ADDRESS;
import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.CONNECTION_MODE_CLOUD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.Broker;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.Cloud;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZeebeClientConfigurationPropertiesTest {

  private static final String BROKER_GRPC_ADDRESS =
      "https://cluster-id.cluster-region.base-url:123";
  private ZeebeClientConfigurationProperties properties;

  @BeforeEach
  public void setUp() {
    properties = new ZeebeClientConfigurationProperties(null);
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

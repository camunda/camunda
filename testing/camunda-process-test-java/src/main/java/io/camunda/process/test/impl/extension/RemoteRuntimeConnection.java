/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.extension;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.process.test.impl.client.HttpClientUtil;
import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteRuntimeConnection implements CamundaRuntimeConnection {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteRuntimeConnection.class);

  private final CloseableHttpClient httpClient = HttpClients.createDefault();

  private final URI camundaRestApiAddress;
  private final URI camundaGrpcApiAddress;
  private final URI camundaMonitoringApiAddress;
  private final URI connectorsRestApiAddress;
  private final Supplier<CamundaClientBuilder> camundaClientBuilderSupplier;

  public RemoteRuntimeConnection(
      final Supplier<CamundaClientBuilder> camundaClientBuilderSupplier,
      final URI camundaMonitoringApiAddress,
      final URI connectorsRestApiAddress) {
    this.camundaClientBuilderSupplier = camundaClientBuilderSupplier;
    final CamundaClientConfiguration clientConfiguration =
        getClientConfiguration(camundaClientBuilderSupplier);
    camundaRestApiAddress = clientConfiguration.getRestAddress();
    camundaGrpcApiAddress = clientConfiguration.getGrpcAddress();
    this.camundaMonitoringApiAddress = camundaMonitoringApiAddress;
    this.connectorsRestApiAddress = connectorsRestApiAddress;
  }

  @Override
  public void start() {
    // nothing to start. the runtime is managed remotely.
    LOGGER.info(
        "Connecting to remote runtime. [Camunda REST: {}, Camunda gRPC: {}, Camunda Monitoring: {}, Connectors REST: {}]",
        camundaRestApiAddress,
        camundaGrpcApiAddress,
        camundaMonitoringApiAddress,
        connectorsRestApiAddress);

    // check connection to remote runtime
    try {
      checkConnection(camundaMonitoringApiAddress.resolve("/actuator/health"));
      LOGGER.info("Connected to remote Camunda runtime successfully.");
    } catch (final IOException e) {
      throw new RuntimeException("Failed to connect to remote Camunda runtime.");
    }

    if (connectorsRestApiAddress != null) {
      try {
        checkConnection(connectorsRestApiAddress.resolve("/actuator/health"));
        LOGGER.info("Connected to remote Connectors runtime successfully.");
      } catch (final IOException e) {
        LOGGER.warn("Failed to connect to remote Connectors runtime.");
      }
    }
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return camundaMonitoringApiAddress;
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return camundaRestApiAddress;
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return camundaGrpcApiAddress;
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    return connectorsRestApiAddress;
  }

  @Override
  public Supplier<CamundaClientBuilder> createClientBuilder() {
    return camundaClientBuilderSupplier;
  }

  private CamundaClientConfiguration getClientConfiguration(
      final Supplier<CamundaClientBuilder> camundaClientBuilderSupplier) {
    final CamundaClientBuilder clientBuilder = camundaClientBuilderSupplier.get();
    try (final CamundaClient camundaClient = clientBuilder.build()) {
      return camundaClient.getConfiguration();
    }
  }

  private void checkConnection(final URI readyEndpoint) throws IOException {
    final HttpGet request = new HttpGet(readyEndpoint);

    httpClient.execute(
        request,
        response -> {
          final int statusCode = response.getCode();
          if (statusCode >= 200 && statusCode < 300) {
            // connection is ready
            return null;
          } else {
            throw new RuntimeException(
                String.format(
                    "Request failed. [code: %d, message: %s]",
                    response.getCode(), HttpClientUtil.getReponseAsString(response)));
          }
        });
  }

  @Override
  public void close() throws Exception {
    // nothing to close. the runtime is managed remotely.
  }
}

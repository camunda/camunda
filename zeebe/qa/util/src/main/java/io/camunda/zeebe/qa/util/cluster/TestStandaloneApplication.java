/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.configuration.Api;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.Data;
import io.camunda.configuration.Monitoring;
import io.camunda.configuration.Processing;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Application that allows to run standalone, with exporters, broker, gateway, etc.
 *
 * <p>Allows to configure and use exporters, the gateway, and broker.
 */
public interface TestStandaloneApplication<T extends TestStandaloneApplication<T>>
    extends TestApplication<T>, TestGateway<T> {

  /**
   * Registers or replaces a new exporter with the given ID. If it was already registered, the
   * existing configuration is passed to the modifier.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  T withExporter(final String id, final Consumer<ExporterCfg> modifier);

  /**
   * Sets the secondary storage type to use.
   *
   * @param type the secondary storage type
   * @return itself for chaining
   */
  T withSecondaryStorageType(SecondaryStorageType type);

  /**
   * Modifies the unified configuration (camunda.* properties). This is the recommended way to
   * configure test brokers going forward.
   *
   * @param modifier a configuration function that accepts the Camunda configuration object
   * @return itself for chaining
   */
  @Override
  default T withUnifiedConfig(final Consumer<Camunda> modifier) {
    throw new UnsupportedOperationException(
        "Unified configuration is not supported by this implementation");
  }

  /**
   * Returns the unified configuration object. This provides access to the camunda.* configuration
   * structure.
   *
   * @return the Camunda unified configuration object
   */
  @Override
  default Camunda unifiedConfig() {
    throw new UnsupportedOperationException(
        "Unified configuration is not supported by this implementation");
  }

  @Override
  default CamundaClientBuilder newClientBuilder() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Cannot create a new client for this application, as it does not have an embedded gateway");
    }

    final CamundaClientBuilder camundaClientBuilder = TestGateway.super.newClientBuilder();

    clientAuthenticationMethod()
        .ifPresent(
            method -> {
              if (method == AuthenticationMethod.BASIC) {
                camundaClientBuilder.credentialsProvider(
                    new BasicAuthCredentialsProviderBuilder()
                        .username(DEFAULT_USER_USERNAME)
                        .password(DEFAULT_USER_PASSWORD)
                        .build());
              }
            });

    return camundaClientBuilder;
  }

  /**
   * Convenience method to modify cluster configuration using the unified configuration API.
   *
   * @param modifier a configuration function for cluster settings
   * @return itself for chaining
   */
  default T withClusterConfig(final Consumer<Cluster> modifier) {
    return withUnifiedConfig(c -> modifier.accept(c.getCluster()));
  }

  /**
   * Convenience method to modify data configuration using the unified configuration API.
   *
   * @param modifier a configuration function for data settings
   * @return itself for chaining
   */
  default T withDataConfig(final Consumer<Data> modifier) {
    return withUnifiedConfig(c -> modifier.accept(c.getData()));
  }

  /**
   * Convenience method to modify API configuration using the unified configuration API.
   *
   * @param modifier a configuration function for API settings
   * @return itself for chaining
   */
  default T withApiConfig(final Consumer<Api> modifier) {
    return withUnifiedConfig(c -> modifier.accept(c.getApi()));
  }

  /**
   * Convenience method to modify processing configuration using the unified configuration API.
   *
   * @param modifier a configuration function for processing settings
   * @return itself for chaining
   */
  default T withProcessingConfig(final Consumer<Processing> modifier) {
    return withUnifiedConfig(c -> modifier.accept(c.getProcessing()));
  }

  /**
   * Convenience method to modify monitoring configuration using the unified configuration API.
   *
   * @param modifier a configuration function for monitoring settings
   * @return itself for chaining
   */
  default T withMonitoringConfig(final Consumer<Monitoring> modifier) {
    return withUnifiedConfig(c -> modifier.accept(c.getMonitoring()));
  }

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  T withSecurityConfig(final Consumer<CamundaSecurityProperties> modifier);

  default Optional<AuthenticationMethod> clientAuthenticationMethod() {
    return Optional.empty();
  }
}

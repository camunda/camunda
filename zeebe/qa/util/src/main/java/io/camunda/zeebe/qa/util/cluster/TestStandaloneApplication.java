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
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
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
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  T withBrokerConfig(final Consumer<LegacyBrokerBasedProperties> modifier);

  /**
   * Modifies the security configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  T withSecurityConfig(final Consumer<CamundaSecurityProperties> modifier);

  LegacyBrokerBasedProperties brokerConfig();

  default Optional<AuthenticationMethod> clientAuthenticationMethod() {
    return Optional.empty();
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
}

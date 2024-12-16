/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeConnector.class);

  @Autowired private IdentityMigrationProperties identityMigrationProperties;

  @Bean
  public ZeebeClient zeebeClient() {
    final var properties = identityMigrationProperties.getZeebe();
    return newZeebeClient(properties);
  }

  public ZeebeClient newZeebeClient(final ZeebeProperties zeebeProperties) {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder().grpcAddress(URI.create(zeebeProperties.getGatewayAddress()));
    if (zeebeProperties.isSecure()) {
      builder.caCertificatePath(zeebeProperties.getCertificatePath());
      LOGGER.info("Use TLS connection to zeebe");
    } else {
      builder.usePlaintext();
      LOGGER.info("Use plaintext connection to zeebe");
    }
    return builder.build();
  }
}

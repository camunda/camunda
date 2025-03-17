/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.config.operate.OperateProperties;
import io.camunda.config.operate.ZeebeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeConnector.class);

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired private OperateProperties operateProperties;

  @Bean // will be closed automatically
  public CamundaClient camundaClient() {
    final var properties = operateProperties.getZeebe();
    return newCamundaClient(properties);
  }

  public CamundaClient newCamundaClient(final ZeebeProperties zeebeProperties) {
    final var gatewayAddress = getGatewayAddress(zeebeProperties);
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .gatewayAddress(gatewayAddress)
            .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE);
    if (zeebeProperties.isSecure()) {
      builder.caCertificatePath(zeebeProperties.getCertificatePath());
      LOGGER.info("Use TLS connection to zeebe");
    } else {
      builder.usePlaintext();
      LOGGER.info("Use plaintext connection to zeebe");
    }
    return builder.build();
  }

  private String getGatewayAddress(final ZeebeProperties properties) {
    final String address;

    final var deprecatedBrokerContactPoint = properties.getBrokerContactPoint();
    final var gatewayAddress = properties.getGatewayAddress();

    if (deprecatedBrokerContactPoint != null) {
      address = deprecatedBrokerContactPoint;
    } else {
      address = gatewayAddress;
    }

    return address;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebe;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.ZeebeProperties;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeConnector {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeConnector.class);

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired
  private OperateProperties operateProperties;

  @Bean //will be closed automatically
  public ZeebeClient zeebeClient() {
    return newZeebeClient(operateProperties.getZeebe());
  }

  public ZeebeClient newZeebeClient(final ZeebeProperties zeebeProperties) {
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
        .gatewayAddress(zeebeProperties.getGatewayAddress())
        .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE);
    if (zeebeProperties.isSecure()) {
      builder.caCertificatePath(zeebeProperties.getCertificatePath());
      logger.info("Use TLS connection to zeebe");
    } else {
      builder.usePlaintext();
      logger.info("Use plaintext connection to zeebe");
    }
    return builder.build();
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe;

import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.camunda.zeebe.client.ZeebeClient;

@Configuration
public class ZeebeConnector {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeConnector.class);

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired
  private OperateProperties operateProperties;

  @Bean //will be closed automatically
  public ZeebeClient zeebeClient() {

    final String gatewayAddress = operateProperties.getZeebe().getGatewayAddress();

    return ZeebeClient
      .newClientBuilder()
      .gatewayAddress(gatewayAddress)
      .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE)
      .usePlaintext()
      .build();
  }

}

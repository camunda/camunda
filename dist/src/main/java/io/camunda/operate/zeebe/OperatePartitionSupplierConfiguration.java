/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.webapps.zeebe.PartitionSupplier;
import io.camunda.webapps.zeebe.PartitionSupplierConfigurer;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.gateway.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OperatePartitionSupplierConfiguration {
  @Bean
  public PartitionSupplier operatePartitionSupplier(
      @Autowired(required = false) final Broker broker,
      @Autowired(required = false) final Gateway gateway,
      @Autowired final CamundaClient camundaClient) {
    return new PartitionSupplierConfigurer(broker, gateway, camundaClient)
        .createPartitionSupplier();
  }
}

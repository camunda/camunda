/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link ExportingRequestBroadcaster} as a single shared bean. It lives in this
 * unconditional {@code io.camunda.zeebe.shared.management} scope (scanned by both the broker and
 * the gateway module) so it is available in every topology where the {@code exporting} actuator
 * runs, including broker-only deployments where the gateway-scoped {@code
 * CamundaServicesConfiguration} is absent. Both the actuator ({@link ExportingEndpoint}) and the
 * per-tenant {@code ExportingServices} inject this instance.
 */
@Configuration(proxyBeanMethods = false)
public class ExportingRequestBroadcasterConfiguration {

  @Bean
  public ExportingRequestBroadcaster exportingRequestBroadcaster(final BrokerClient brokerClient) {
    return new ExportingRequestBroadcaster(brokerClient);
  }
}

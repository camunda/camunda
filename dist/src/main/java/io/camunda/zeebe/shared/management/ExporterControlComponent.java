/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.exporting.ExportingControlApi;
import io.camunda.zeebe.gateway.admin.exporting.ExportingControlService;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ExporterControlComponent {

  @Bean
  ExportingControlApi controlApi(final BrokerClient client) {
    return new ExportingControlService(client);
  }
}

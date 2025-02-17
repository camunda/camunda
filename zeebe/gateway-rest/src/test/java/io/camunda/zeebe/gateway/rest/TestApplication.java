/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.search.clients.AlertDefinitionClient;
import io.camunda.service.AlertDefinitionServices;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    exclude = {
      SecurityAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      DataSourceAutoConfiguration.class
    })
public class TestApplication {
  // required to provide the web server context

  @Bean
  AlertDefinitionClient alertDefinitionClient() {
    return new InMemoryAlertDefinitionClient();
  }

  @Bean
  public AlertDefinitionServices alertDefinitionServices(
      final AlertDefinitionClient alertDefinitionClient) {
    return new AlertDefinitionServices(alertDefinitionClient, null, null, null);
  }
}

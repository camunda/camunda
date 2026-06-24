/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class SystemControllerTestConfiguration {

  @Bean
  @Primary
  public WebappConfiguration testWebappConfiguration() {
    final WebappConfiguration config = new WebappConfiguration();
    config.setActiveComponents(List.of("admin", "operate", "tasklist"));
    return config;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {CommonsModuleConfiguration.class},
    properties = {
      "camunda.rest.process-cache.max-size=1000",
      "camunda.rest.process-cache.expiration-idle-millis=360000",
      CREATE_SCHEMA_PROPERTY + "=false",
    })
public class GatewayRestConfigurationTest {

  @Autowired protected GatewayRestConfiguration configuration;

  @Test
  public void shouldSetProcessCacheConfiguration() {
    assertThat(configuration.getProcessCache().getMaxSize()).isEqualTo(1000);
    assertThat(configuration.getProcessCache().getExpirationIdleMillis()).isEqualTo(360000);
  }
}

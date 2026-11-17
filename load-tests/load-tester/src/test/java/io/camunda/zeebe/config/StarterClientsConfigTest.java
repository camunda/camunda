/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientPropertiesResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Loads {@code application.yaml} with the {@code starter} profile and resolves the named clients
 * without creating the {@code Starter} bean, so no broker is needed.
 */
@SpringJUnitConfig
@ContextConfiguration(
    classes = StarterClientsConfigTest.class,
    initializers = ConfigDataApplicationContextInitializer.class)
@ActiveProfiles("starter")
class StarterClientsConfigTest {

  @Autowired private Environment environment;

  @Test
  void shouldConfigureMainAndQueryClientsInheritingClientDefaults() {
    // given / when
    final MultiCamundaClientProperties clients =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then
    assertThat(clients.getClients()).containsOnlyKeys("main", "query");
    assertThat(clients.getPrimaryClientName()).contains("main");
    clients
        .getClients()
        .values()
        .forEach(
            client -> {
              assertThat(client.getRestAddress()).hasToString("http://localhost:8080");
              assertThat(client.getGrpcAddress()).hasToString("http://localhost:26500");
              assertThat(client.getPreferRestOverGrpc()).isTrue();
              assertThat(client.isUseClientSideLoadBalancing()).isTrue();
              assertThat(client.getExecutionThreads()).isZero();
            });
  }
}

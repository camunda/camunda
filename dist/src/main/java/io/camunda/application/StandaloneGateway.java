/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.initializers.HealthConfigurationInitializer;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneGateway {

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/zeebe_gateway_banner.txt");

    final var standaloneGatewayApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(
                CommonsModuleConfiguration.class,
                GatewayModuleConfiguration.class,
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                GatewayBasedPropertiesOverride.class)
            .profiles(Profile.GATEWAY.getId(), Profile.STANDALONE.getId())
            .initializers(new HealthConfigurationInitializer())
            .build(args);

    standaloneGatewayApplication.run();
  }
}

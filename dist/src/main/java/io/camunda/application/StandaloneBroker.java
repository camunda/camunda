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
import io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.IdleStrategyPropertiesOverride;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneBroker {

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/zeebe_broker_banner.txt");

    final var standaloneBrokerApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(
                CommonsModuleConfiguration.class,
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                BrokerBasedPropertiesOverride.class,
                ActorClockControlledPropertiesOverride.class,
                IdleStrategyPropertiesOverride.class,
                BrokerModuleConfiguration.class)
            .profiles(Profile.BROKER.getId(), Profile.STANDALONE.getId())
            .initializers(new HealthConfigurationInitializer())
            .build(args);

    standaloneBrokerApplication.run();
  }
}

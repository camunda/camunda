/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.springframework.core.env.AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.initializers.DefaultAuthenticationInitializer;
import io.camunda.application.initializers.HealthConfigurationInitializer;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.IdleStrategyPropertiesOverride;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.identity.IdentityModuleConfiguration;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneCamunda {

  private static final String SPRING_PROFILES_ACTIVE_PROPERTY = ACTIVE_PROFILES_PROPERTY_NAME;
  private static final String DEFAULT_CAMUNDA_PROFILES =
      String.join(
          ",",
          Profile.OPERATE.getId(),
          Profile.TASKLIST.getId(),
          Profile.BROKER.getId(),
          Profile.IDENTITY.getId(),
          Profile.CONSOLIDATED_AUTH.getId());

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");

    final var defaultProperties = getDefaultProperties();
    final var standaloneCamundaApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(
                // Unified Configuration classes
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                TasklistPropertiesOverride.class,
                OperatePropertiesOverride.class,
                GatewayBasedPropertiesOverride.class,
                BrokerBasedPropertiesOverride.class,
                ActorClockControlledPropertiesOverride.class,
                IdleStrategyPropertiesOverride.class,
                // ---
                CommonsModuleConfiguration.class,
                OperateModuleConfiguration.class,
                TasklistModuleConfiguration.class,
                IdentityModuleConfiguration.class,
                WebappsModuleConfiguration.class,
                BrokerModuleConfiguration.class,
                GatewayModuleConfiguration.class)
            // https://docs.spring.io/spring-boot/docs/2.3.9.RELEASE/reference/html/spring-boot-features.html#boot-features-external-config
            // Default properties are only used, if not overridden by any other config
            .properties(defaultProperties)
            .initializers(
                new DefaultAuthenticationInitializer(),
                new HealthConfigurationInitializer(),
                new WebappsConfigurationInitializer())
            .listeners(new ApplicationErrorListener())
            .build(args);

    standaloneCamundaApplication.run(args);
  }

  public static Map<String, Object> getDefaultProperties() {
    final var defaultProperties = new HashMap<String, Object>();
    defaultProperties.put(SPRING_PROFILES_ACTIVE_PROPERTY, DEFAULT_CAMUNDA_PROFILES);
    // Per default, we target a green field installation with the StandaloneCamunda application.
    // Meaning, we will disable the importers per default
    // Importers can still be enabled by configuration
    defaultProperties.put("camunda.operate.importerEnabled", "false");
    defaultProperties.put("camunda.tasklist.importerEnabled", "false");
    return defaultProperties;
  }
}

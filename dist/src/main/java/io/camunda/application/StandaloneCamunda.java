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
import io.camunda.application.initializers.McpGatewayInitializer;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.beanoverrides.IdleStrategyPropertiesOverride;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.PrimaryStorageBackupPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
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
          Profile.ADMIN.getId(),
          Profile.CONSOLIDATED_AUTH.getId());

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");

    final var defaultProperties = getDefaultProperties(true);
    final var standaloneCamundaApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(
                // Unified Configuration classes
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                TasklistPropertiesOverride.class,
                OperatePropertiesOverride.class,
                PrimaryStorageBackupPropertiesOverride.class,
                GatewayBasedPropertiesOverride.class,
                BrokerBasedPropertiesOverride.class,
                ActorClockControlledPropertiesOverride.class,
                IdleStrategyPropertiesOverride.class,
                GatewayRestPropertiesOverride.class,
                SearchEngineConnectPropertiesOverride.class,
                SearchEngineIndexPropertiesOverride.class,
                SearchEngineRetentionPropertiesOverride.class,
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
                new WebappsConfigurationInitializer(),
                new McpGatewayInitializer())
            .listeners(new ApplicationErrorListener())
            .build(args);

    standaloneCamundaApplication.run(args);
  }

  public static Map<String, Object> getDefaultProperties(final boolean withProfiles) {
    final var defaultProperties = new HashMap<String, Object>();

    if (withProfiles) {
      defaultProperties.put(SPRING_PROFILES_ACTIVE_PROPERTY, DEFAULT_CAMUNDA_PROFILES);
    }
    defaultProperties.put("management.health.defaults.enabled", false);
    defaultProperties.put("spring.web.resources.add-mappings", false);
    defaultProperties.put("spring.thymeleaf.check-template-location", false);
    defaultProperties.put(
        "camunda.security.multiTenancy.checksEnabled",
        "${zeebe.broker.gateway.multiTenancy.enabled:false}");

    return defaultProperties;
  }
}

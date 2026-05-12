/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.beans.WebappProperties;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration.Cloud;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Produces the authoritative {@link WebappProperties} bean used by the REST layer. Unified {@code
 * camunda.webapp.*} keys take precedence; when absent, legacy per-app keys from Operate and
 * Tasklist are used as fallbacks so existing deployments require no config changes.
 */
@Configuration
@Profile("!restore")
@EnableConfigurationProperties({
  WebappProperties.class,
  LegacyOperateProperties.class,
  LegacyTasklistProperties.class
})
@DependsOn("unifiedConfigurationHelper")
public class WebappPropertiesOverride {

  private static final List<String> KNOWN_COMPONENTS = List.of("admin", "operate", "tasklist");

  private final WebappProperties webappProperties;
  private final LegacyOperateProperties legacyOperateProperties;
  private final LegacyTasklistProperties legacyTasklistProperties;
  private final ConfigurableEnvironment environment;

  public WebappPropertiesOverride(
      final WebappProperties webappProperties,
      final LegacyOperateProperties legacyOperateProperties,
      final LegacyTasklistProperties legacyTasklistProperties,
      final ConfigurableEnvironment environment) {
    this.webappProperties = webappProperties;
    this.legacyOperateProperties = legacyOperateProperties;
    this.legacyTasklistProperties = legacyTasklistProperties;
    this.environment = environment;
  }

  @Bean
  @Primary
  public WebappProperties webappProperties() {
    final WebappProperties resolved = new WebappProperties();
    BeanUtils.copyProperties(webappProperties, resolved);

    applyEnterpriseFallback(resolved);
    applyCloudFallbacks(resolved);
    resolved.setActiveComponents(computeActiveComponents());

    return resolved;
  }

  private List<String> computeActiveComponents() {
    return KNOWN_COMPONENTS.stream().filter(this::isComponentUiEnabled).toList();
  }

  private boolean isComponentUiEnabled(final String name) {
    return environment.getProperty("camunda." + name + ".webappEnabled", Boolean.class, true)
        && environment.getProperty("camunda.webapps." + name + ".enabled", Boolean.class, true)
        && environment.getProperty("camunda.webapps." + name + ".ui-enabled", Boolean.class, true);
  }

  private void applyEnterpriseFallback(final WebappProperties target) {
    if (!webappProperties.isEnterprise()
        && (legacyOperateProperties.isEnterprise() || legacyTasklistProperties.isEnterprise())) {
      target.setEnterprise(
          legacyOperateProperties.isEnterprise() || legacyTasklistProperties.isEnterprise());
    }
  }

  private void applyCloudFallbacks(final WebappProperties target) {
    final Cloud cloud = target.getCloud();

    if (cloud.getStage() == null && legacyTasklistProperties.getCloud().getStage() != null) {
      cloud.setStage(legacyTasklistProperties.getCloud().getStage());
    }

    if (cloud.getMixpanelToken() == null) {
      if (legacyOperateProperties.getCloud().getMixpanelToken() != null) {
        cloud.setMixpanelToken(legacyOperateProperties.getCloud().getMixpanelToken());
      } else if (legacyTasklistProperties.getCloud().getMixpanelToken() != null) {
        cloud.setMixpanelToken(legacyTasklistProperties.getCloud().getMixpanelToken());
      }
    }

    if (cloud.getMixpanelApiHost() == null) {
      if (legacyOperateProperties.getCloud().getMixpanelAPIHost() != null) {
        cloud.setMixpanelApiHost(legacyOperateProperties.getCloud().getMixpanelAPIHost());
      } else if (legacyTasklistProperties.getCloud().getMixpanelAPIHost() != null) {
        cloud.setMixpanelApiHost(legacyTasklistProperties.getCloud().getMixpanelAPIHost());
      }
    }
  }
}

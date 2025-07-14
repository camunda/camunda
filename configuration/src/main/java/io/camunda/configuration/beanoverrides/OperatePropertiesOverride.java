/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties(OperateProperties.class)
@PropertySource("classpath:operate-version.properties")
@DependsOn("databaseInfo")
@Profile("operate | test")
public class OperatePropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final OperateProperties legacyOperateProperties;

  public OperatePropertiesOverride(
      UnifiedConfiguration unifiedConfiguration, OperateProperties legacyOperateProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyOperateProperties = legacyOperateProperties;
  }

  @Bean
  @Primary
  public OperateProperties operateProperties() {
    final OperateProperties override = new OperateProperties();
    BeanUtils.copyProperties(legacyOperateProperties, override);

    // TODO: Populate the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties(LegacyTasklistProperties.class)
@PropertySource("classpath:tasklist-version.properties")
public class TasklistPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyTasklistProperties legacyTasklistProperties;

  public TasklistPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyTasklistProperties legacyTasklistProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyTasklistProperties = legacyTasklistProperties;
  }

  @Bean
  @Primary
  public TasklistProperties tasklistProperties() {
    final TasklistProperties override = new TasklistProperties();
    BeanUtils.copyProperties(legacyTasklistProperties, override);

    // TODO: Populate the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }
}

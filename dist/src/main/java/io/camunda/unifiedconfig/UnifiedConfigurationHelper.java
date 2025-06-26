/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.LegacyBrokerBasedProperties;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.property.LegacyOperateProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.LegacyTasklistProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationHelper {

  private static Environment environment;

  private UnifiedConfigurationHelper(@Autowired Environment environment) {
    UnifiedConfigurationHelper.environment = environment;
  }

  public static String getLegacyConfigValue(String legacyConfigKey) {
    return environment.getProperty(legacyConfigKey);
  }

  @Bean
  public TasklistProperties tasklistProperties(
      final LegacyTasklistProperties legacyTasklistProperties,
      final UnifiedConfiguration unifiedConfiguration) {
    TasklistProperties patchedTasklistProperties = new TasklistProperties();
    BeanUtils.copyProperties(legacyTasklistProperties, patchedTasklistProperties);

    // TODO: Patch patchedTasklistProperties using unifiedConfiguration

    return patchedTasklistProperties;
  }

  @Bean
  public OperateProperties operateProperties(
      final LegacyOperateProperties legacyOperateProperties,
      final UnifiedConfiguration unifiedConfiguration) {
    OperateProperties patchedOperateProperties = new OperateProperties();
    BeanUtils.copyProperties(legacyOperateProperties, patchedOperateProperties);

    // TODO: Patch patchedOperateProperties using unifiedConfiguration

    return patchedOperateProperties;
  }

  @Bean
  public BrokerBasedProperties brokerBasedProperties(
      final LegacyBrokerBasedProperties legacyBrokerBasedProperties,
      final UnifiedConfiguration unifiedConfiguration) {
    BrokerBasedProperties patchedBrokerBasedProperties = new BrokerBasedProperties();
    BeanUtils.copyProperties(legacyBrokerBasedProperties, patchedBrokerBasedProperties);

    // TODO: Patch patchedBrokerBasedProperties using unifiedConfiguration

    return patchedBrokerBasedProperties;
  }

  private static void putArg(Map<String, Object> args, String keyPath, Object value) {
    String[] keys = keyPath.split("\\.");
    Map<String, Object> pointer = args;

    for(int i=0; i<keys.length - 1; i++) {
      String key = keys[i];

      // if child doesn't exist, create it
      if(pointer.get(key) == null ) {
        pointer.put(key, new HashMap<String, Object>());
      }

      pointer = (Map<String, Object>) pointer.get(key);
    }

    pointer.put(keys[keys.length - 1], value);
  }
}

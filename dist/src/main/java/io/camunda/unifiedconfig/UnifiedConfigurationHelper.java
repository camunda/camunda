/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.model.bpmn.instance.Task;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationHelper {

  private static final String RANDOM_SUFFIX = "-sample";

  @PostConstruct
  public void init() {
    System.out.println("Breakpoint here and check the object config");
  }

  @Bean
  public TasklistProperties tasklistProperties(final UnifiedConfiguration unifiedConfiguration) {
    Database databaseCfg = unifiedConfiguration.getCamunda().getData().getDatabase();

    TasklistProperties tasklistProperties = new TasklistProperties();
    tasklistProperties.getElasticsearch().setUrl(
        databaseCfg.getElasticsearch().getUrl() + RANDOM_SUFFIX);

    return tasklistProperties;
  }

  @Bean
  public OperateProperties operateProperties(final UnifiedConfiguration unifiedConfiguration) {
    Database databaseCfg = unifiedConfiguration.getCamunda().getData().getDatabase();

    OperateProperties operateProperties = new OperateProperties();
    operateProperties.getElasticsearch().setUrl(
        databaseCfg.getElasticsearch().getUrl() + RANDOM_SUFFIX);

    return operateProperties;
  }

  public static void populateBrokerCfg(
      UnifiedConfiguration unifiedConfiguration,
      BrokerCfg brokerCfg) {
    Database databaseConfig = unifiedConfiguration.getCamunda().getData().getDatabase();

    // TODO: is the exporter name stored anywhere in the unified config?
    ExporterCfg exporterCfg = brokerCfg.getExporters().get("camundaexporter");
    Map<String, Object> args = exporterCfg.getArgs();

    putArg(args, "connect.url", databaseConfig.getElasticsearch().getUrl());
    putArg(args, "index.prefix", databaseConfig.getElasticsearch().getIndexPrefix());
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

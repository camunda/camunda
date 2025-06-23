/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationHelper {

  @Autowired
  UnifiedConfiguration config;

  @PostConstruct
  public void init() {
    System.out.println("Breakpoint here and check the object config");
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

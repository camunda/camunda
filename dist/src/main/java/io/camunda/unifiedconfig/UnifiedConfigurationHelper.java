/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.PartialOperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.model.bpmn.instance.Task;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontProperties.Metrics.Export;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UnifiedConfiguration.class)
public class UnifiedConfigurationHelper {

  @PostConstruct
  public void init() {
    System.out.println("Breakpoint here and check the object config");
  }

  @Bean
  public TasklistProperties tasklistProperties(final UnifiedConfiguration unifiedConfiguration) {
    Database databaseCfg = unifiedConfiguration.getCamunda().getData().getDatabase();

    TasklistProperties tasklistProperties = new TasklistProperties();
    tasklistProperties.getElasticsearch().setUrl(
        databaseCfg.getElasticsearch().getUrl());

    // TODO: There should be a cluster name within the elasticsearch node, but I can't
    //  find it in the technical vision document. We need to figure out. I think it's
    //  used for logging purposes.

    return tasklistProperties;
  }

  @Bean
  public OperateProperties partialOperateProperties(
      final UnifiedConfiguration unifiedConfiguration) {
    OperateProperties operateProperties = new OperateProperties();
    Database databaseCfg = unifiedConfiguration.getCamunda().getData().getDatabase();

    operateProperties.getElasticsearch().setUrl(
        databaseCfg.getElasticsearch().getUrl());

    // TODO: There should be a cluster name within the elasticsearch node, but I can't
    //  find it in the technical vision document. We need to figure out. I think it's
    //  used for logging purposes.

    return operateProperties;
  }

  @Bean
  public BrokerBasedProperties brokerBasedProperties(final UnifiedConfiguration unifiedConfiguration) {
    BrokerBasedProperties brokerBasedProperties = new BrokerBasedProperties();
    Database databaseConfig = unifiedConfiguration.getCamunda().getData().getDatabase();
    Map<String, ExporterCfg> exporters = brokerBasedProperties.getExporters();

    // CamundaExporter is the default
    ExporterCfg camundaExporter = new ExporterCfg();
    camundaExporter.setClassName(CamundaExporter.class.getCanonicalName());

    Map<String, Object> args = new HashMap<>();
    camundaExporter.setArgs(args);

    exporters.put("camundaexporter", camundaExporter);
    putArg(args, "connect.url", databaseConfig.getElasticsearch().getUrl());
    putArg(args, "index.prefix", databaseConfig.getElasticsearch().getIndexPrefix());

    // TODO: We need to configure the potential user-configured additional exporters as well

    return brokerBasedProperties;
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

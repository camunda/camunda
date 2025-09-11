/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import io.camunda.client.CamundaClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

@Configuration
@ComponentScan(
    basePackages = "io.camunda.operate.data",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile({"dev-data", "usertest-data"})
public class DataGeneratorModuleConfiguration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DataGeneratorModuleConfiguration.class);

  @PostConstruct
  public void logModule() {
    LOGGER.info("Starting module: data generator");
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaClient camundaClient() {
    return CamundaClient.newClientBuilder().build();
  }
}

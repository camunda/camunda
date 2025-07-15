/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestOperatePropertiesOverride.TestOperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties(TestOperateProperties.class)
@PropertySource("classpath:tasklist-version.properties")
public class TestOperatePropertiesOverride {

  private final TestOperateProperties testOperateProperties;

  @Autowired
  public TestOperatePropertiesOverride(final TestOperateProperties testOperateProperties) {
    this.testOperateProperties = testOperateProperties;
  }

  @Bean
  public OperateProperties operateProperties() {
    return testOperateProperties;
  }

  @ConfigurationProperties(OperateProperties.PREFIX)
  public static class TestOperateProperties extends OperateProperties {}
}

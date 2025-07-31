/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.apps.modules;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.operate.util.TestApplication;
import io.camunda.webapps.WebappsModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(
    basePackages = {"io.camunda.operate", "io.camunda.application.commons"},
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.util\\.apps\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.archiver\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.data\\..*"),
      @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.camunda\\.operate\\.it\\..*"),
      @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = TestApplication.class),
      @ComponentScan.Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          value = OperateModuleConfiguration.class)
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import({
  WebappsModuleConfiguration.class,
  OperatePropertiesOverride.class,
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  GatewayBasedPropertiesOverride.class,
})
public class ModulesTestApplication {

  public static void main(final String[] args) throws Exception {
    SpringApplication.run(ModulesTestApplication.class, args);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util.apps.modules;

import io.camunda.tasklist.Application;
import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.util.TestApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@ComponentScan(
    basePackages = "io.camunda.tasklist",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.util\\.apps\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.archiver\\..*"),
      @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.camunda\\.tasklist\\.it\\..*"),
      @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = TestApplication.class),
      @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Application.class)
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class ModulesTestApplication {

  public static void main(String[] args) throws Exception {
    SpringApplication.run(ModulesTestApplication.class, args);
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    return DataGenerator.DO_NOTHING;
  }
}

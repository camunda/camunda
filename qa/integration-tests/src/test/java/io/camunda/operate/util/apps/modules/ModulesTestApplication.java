/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.apps.modules;

import io.camunda.operate.Application;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.data.DataGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@ComponentScan(basePackages = "io.camunda.operate",
  excludeFilters = {
    @ComponentScan.Filter(type=FilterType.REGEX,pattern="io\\.camunda\\.operate\\.util\\.apps\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="io\\.camunda\\.operate\\.zeebeimport\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="io\\.camunda\\.operate\\.webapp\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="io\\.camunda\\.operate\\.archiver\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="io\\.camunda\\.operate\\.it\\..*"),
      @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,value = TestApplication.class),
      @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,value = Application.class)
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

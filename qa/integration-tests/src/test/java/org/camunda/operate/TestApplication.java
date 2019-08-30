/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import org.camunda.operate.data.DataGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = "org.camunda.operate",
  excludeFilters = {
    @ComponentScan.Filter(type=FilterType.REGEX,pattern="org\\.camunda\\.operate\\.util\\.apps\\..*"),
    @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,value = Application.class),
  })
public class TestApplication {

  public static void main(String[] args) throws Exception {
    SpringApplication.run(TestApplication.class, args);
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    return DataGenerator.DO_NOTHING;
  }

}

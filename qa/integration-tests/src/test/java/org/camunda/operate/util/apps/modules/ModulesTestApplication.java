/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util.apps.modules;

import org.camunda.operate.Application;
import org.camunda.operate.TestApplication;
import org.camunda.operate.data.DataGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@ComponentScan(basePackages = "org.camunda.operate",
  excludeFilters = {
    @ComponentScan.Filter(type=FilterType.REGEX,pattern="org\\.camunda\\.operate\\.util\\.apps\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.zeebeimport\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.webapp\\..*"),
      @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.it\\..*"),
      @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,value = TestApplication.class),
      @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,value = Application.class)
  })
public class ModulesTestApplication {

  public static void main(String[] args) throws Exception {
    SpringApplication.run(ModulesTestApplication.class, args);
  }

  @Bean(name = "passwordEncoder")
  public PasswordEncoder getPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    return DataGenerator.DO_NOTHING;
  }

}

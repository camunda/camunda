/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import java.util.Arrays;

import org.camunda.operate.data.DataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = "org.camunda.operate",
    excludeFilters = {
        @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.zeebeimport\\..*"),
        @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.webapp\\..*")
    })
@EnableAutoConfiguration
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws Exception {

    //To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(Application.class);
    springApplication.setAddCommandLineProperties(true);
    ensureOneAuthProfileIsSet(args, springApplication).run(args);
  }
  
  protected static SpringApplication ensureOneAuthProfileIsSet(String[] args,SpringApplication springApplication) {
    String profilesFromEnv = String.format("%s", System.getenv("SPRING_PROFILES_ACTIVE"));
    String profilesFromArgs = String.join(",",Arrays.asList(args));
    if(! profilesFromArgs.contains("auth") && !profilesFromEnv.contains("auth")) {
      springApplication.setAdditionalProfiles("auth");
    }
    return springApplication;
  }
  
  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    logger.debug("Create Data generator stub");
    return DataGenerator.DO_NOTHING;
  }

}


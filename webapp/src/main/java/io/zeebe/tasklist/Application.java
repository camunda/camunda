/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist;

import io.zeebe.tasklist.data.DataGenerator;
import java.util.Arrays;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@ComponentScan(
    basePackages = "io.zeebe.tasklist",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.zeebe\\.tasklist\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.zeebe\\.tasklist\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.zeebe\\.tasklist\\.archiver\\..*")
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@EnableAutoConfiguration
public class Application {

  private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws Exception {

    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(Application.class);
    // use fully qualified names as bean name, as we have classes with same names for different
    // versions of importer
    springApplication.setAddCommandLineProperties(true);
    if (!isOneAuthProfileActive(args)) {
      springApplication.setAdditionalProfiles("auth");
    }

    // GraphQL inspection tool is disabled by default
    // Exception handler is enabled
    springApplication.setDefaultProperties(
        Map.of(
            "graphql.playground.enabled",
            "false",
            "graphql.servlet.exception-handlers-enabled",
            "true"));

    springApplication.run(args);
  }

  protected static boolean isOneAuthProfileActive(String[] args) {
    final String profilesFromEnv = String.format("%s", System.getenv("SPRING_PROFILES_ACTIVE"));
    final String profilesFromArgs = String.join(",", Arrays.asList(args));
    final String profilesFromProperties =
        String.format("%s", System.getProperty("spring.profiles.active"));
    return profilesFromArgs.contains("auth")
        || profilesFromEnv.contains("auth")
        || profilesFromProperties.contains("auth");
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    LOGGER.debug("Create Data generator stub");
    return DataGenerator.DO_NOTHING;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.camunda.operate.data.DataGenerator;
import org.camunda.operate.webapp.security.OperateURIs;
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
@ComponentScan(basePackages = "org.camunda.operate",
    excludeFilters = {
        @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.zeebeimport\\..*"),
        @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.webapp\\..*"),
        @ComponentScan.Filter(type= FilterType.REGEX,pattern="org\\.camunda\\.operate\\.archiver\\..*")
    },
    //use fully qualified names as bean name, as we have classes with same names for different versions of importer
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@EnableAutoConfiguration
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) {

    //To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(Application.class);
    springApplication.setAddCommandLineProperties(true);

    if(!isOneAuthProfileActive(args)) {
      springApplication.setAdditionalProfiles(OperateURIs.AUTH_PROFILE);
    }
    setDefaultProperties(springApplication);

    springApplication.run(args);
  }

  private static void setDefaultProperties(final SpringApplication springApplication) {
    final Map<String, Object> propsMap = new HashMap<>();
    propsMap.putAll(getManagementProperties());
    springApplication.setDefaultProperties(propsMap);
  }

  public static Map<String, Object> getManagementProperties() {
    return Map.of(
        //disable default health indicators: https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-health-indicators
        "management.health.defaults.enabled", "false",

        //enable Kubernetes health groups: https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
        "management.endpoint.health.probes.enabled", "true",

        //enable health check and metrics endpoints
        "management.endpoints.web.exposure.include", "health, prometheus",

        //add custom check to standard readiness check
        "management.endpoint.health.group.readiness.include", "readinessState,elsIndicesCheck"
    );
  }

  protected static boolean isOneAuthProfileActive(String[] args) {
    String profilesFromEnv = String.format("%s", System.getenv("SPRING_PROFILES_ACTIVE"));
    String profilesFromArgs = String.join(",",Arrays.asList(args));
    String profilesFromProperties = String.format("%s", System.getProperty("spring.profiles.active"));
    return profilesFromArgs.contains("auth") || profilesFromEnv.contains("auth") || profilesFromProperties.contains("auth");
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    logger.debug("Create Data generator stub");
    return DataGenerator.DO_NOTHING;
  }

}


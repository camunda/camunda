/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import static io.camunda.operate.property.OperateApplicationProperties.OPERATE_STATIC_RESOURCES_LOCATION;
import static io.camunda.operate.property.OperateApplicationProperties.SPRING_THYMELEAF_PREFIX_KEY;
import static io.camunda.operate.property.OperateApplicationProperties.SPRING_THYMELEAF_PREFIX_VALUE;

import io.camunda.operate.property.OperateApplicationProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootApplication
@ComponentScan(
    basePackages = "io.camunda.operate",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.operate\\.archiver\\..*"),
      @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.camunda\\.operate\\.data\\..*")
    },
    // use fully qualified names as bean name, as we have classes with same names for different
    // versions of importer
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class StandaloneOperate implements OperateMainApplication {

  public static void main(final String[] args) {

    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    System.setProperty(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");
    // Hack for the moment to allow serving static resources in Operate.
    // Must be removed with the single application.
    System.setProperty("spring.web.resources.add-mappings", "true");
    System.setProperty("spring.web.resources.static-locations", OPERATE_STATIC_RESOURCES_LOCATION);
    System.setProperty("spring.banner.location", "classpath:/operate-banner.txt");
    final SpringApplication springApplication = new SpringApplication(StandaloneOperate.class);
    // add "operate" profile, so that application-operate.yml gets loaded. This is a way to not
    // load other components' 'application-{component}.yml'
    springApplication.setAdditionalProfiles("operate");
    springApplication.setAddCommandLineProperties(true);
    springApplication.addListeners(new ApplicationErrorListener());
    setDefaultProperties(springApplication);
    setDefaultAuthProfile(springApplication);
    springApplication.run(args);
  }

  private static void setDefaultAuthProfile(final SpringApplication springApplication) {
    springApplication.addInitializers(
        configurableApplicationContext -> {
          final ConfigurableEnvironment env = configurableApplicationContext.getEnvironment();
          final Set<String> activeProfiles = Set.of(env.getActiveProfiles());
          if (OperateProfileService.AUTH_PROFILES.stream().noneMatch(activeProfiles::contains)) {
            env.addActiveProfile(OperateProfileService.DEFAULT_AUTH);
          }
        });
  }

  private static void setDefaultProperties(final SpringApplication springApplication) {
    final Map<String, Object> defaultsProperties = new HashMap<>();
    defaultsProperties.putAll(getWebProperties());
    defaultsProperties.putAll(OperateApplicationProperties.getManagementProperties());
    springApplication.setDefaultProperties(defaultsProperties);
  }

  private static Map<String, Object> getWebProperties() {
    return Map.of(
        "server.servlet.session.cookie.name",
        "OPERATE-SESSION",
        "spring.thymeleaf.check-template-location",
        "true",
        SPRING_THYMELEAF_PREFIX_KEY,
        SPRING_THYMELEAF_PREFIX_VALUE,
        "spring.mvc.pathmatch.matching-strategy",
        "ANT_PATH_MATCHER",
        // Return error messages for all endpoints by default, except for Internal API.
        // Internal API error handling is defined in InternalAPIErrorController.
        "server.error.include-message",
        "always");
  }

  public static class ApplicationErrorListener
      implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(final ApplicationFailedEvent event) {
      event.getApplicationContext().close();
      System.exit(-1);
    }
  }
}

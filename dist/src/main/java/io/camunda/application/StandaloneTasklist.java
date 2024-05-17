/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_PROFILES;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.DEFAULT_AUTH;

import graphql.kickstart.autoconfigure.annotations.GraphQLAnnotationsAutoConfiguration;
import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.WebappsModuleConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootApplication(
    exclude = {
      ElasticsearchClientAutoConfiguration.class,
      GraphQLAnnotationsAutoConfiguration.class
    })
@ComponentScan(
    basePackages = "io.camunda.tasklist",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.zeebeimport\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.webapp\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.tasklist\\.archiver\\..*")
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import(WebappsModuleConfiguration.class)
public class StandaloneTasklist {

  public static final String TASKLIST_STATIC_RESOURCES_LOCATION =
      "classpath:/META-INF/resources/tasklist/";
  private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneTasklist.class);

  public static void main(final String[] args) {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    System.setProperty(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");
    System.setProperty("spring.web.resources.static-locations", TASKLIST_STATIC_RESOURCES_LOCATION);
    System.setProperty("spring.banner.location", "classpath:/tasklist-banner.txt");
    // We need to disable this property in Tasklist (enabled in dist/application.properties),
    // otherwise ForwardErrorController does not get invoked
    // System.setProperty("spring.mvc.problemdetails.enabled", "false");
    final SpringApplication springApplication = new SpringApplication(StandaloneTasklist.class);
    // add "tasklist" profile, so that application-tasklist.yml gets loaded. This is a way to not
    // load other components' 'application-{component}.yml'
    springApplication.setAdditionalProfiles("tasklist");
    // use fully qualified names as bean name, as we have classes with same names for different
    // versions of importer
    springApplication.setAddCommandLineProperties(true);
    springApplication.addListeners(new ApplicationErrorListener());
    setDefaultProperties(springApplication);
    setDefaultAuthProfile(springApplication);
    springApplication.run(args);
  }

  private static void setDefaultProperties(final SpringApplication springApplication) {
    final Map<String, Object> defaultProperties = new HashMap<>();
    defaultProperties.putAll(getManagementProperties());
    defaultProperties.putAll(getGraphqlProperties());
    defaultProperties.putAll(getWebProperties());
    springApplication.setDefaultProperties(defaultProperties);
  }

  private static void setDefaultAuthProfile(final SpringApplication springApplication) {
    springApplication.addInitializers(
        configurableApplicationContext -> {
          final ConfigurableEnvironment env = configurableApplicationContext.getEnvironment();
          final Set<String> activeProfiles = Set.of(env.getActiveProfiles());
          if (AUTH_PROFILES.stream().noneMatch(activeProfiles::contains)) {
            env.addActiveProfile(DEFAULT_AUTH);
          }
        });
  }

  private static Map<String, Object> getGraphqlProperties() {
    // GraphQL's inspection tool is disabled by default
    // Exception handler is enabled
    return Map.of(
        "graphql.playground.enabled", "false",
        "graphql.servlet.exception-handlers-enabled", "true",
        "graphql.extended-scalars", "DateTime",
        "graphql.schema-strategy", "annotations",
        "graphql.annotations.base-package", "io.camunda.tasklist",
        "graphql.annotations.always-prettify", "false",
        "graphql.annotations.input-prefix", "");
  }

  private static Map<String, Object> getWebProperties() {
    return Map.of(
        "server.servlet.session.cookie.name",
        TasklistURIs.COOKIE_JSESSIONID,
        "spring.mvc.pathmatch.matching-strategy",
        "ANT_PATH_MATCHER",
        // Return error messages for all endpoints by default, except for Internal API.
        // Internal API error handling is defined in InternalAPIErrorController.
        "server.error.include-message",
        "always");
  }

  public static Map<String, Object> getManagementProperties() {
    return Map.of(
        // disable default health indicators:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-health-indicators
        "management.health.defaults.enabled", "false",

        // enable Kubernetes health groups:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
        "management.endpoint.health.probes.enabled", "true",

        // enable health check and metrics endpoints
        "management.endpoints.web.exposure.include",
            "health, prometheus, loggers, usage-metrics, backups",

        // add custom check to standard readiness check
        "management.endpoint.health.group.readiness.include", "readinessState,searchEngineCheck");
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    LOGGER.debug("Create Data generator stub");
    return DataGenerator.DO_NOTHING;
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

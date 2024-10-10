/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.Metrics;
import io.camunda.operate.schema.SchemaStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(
    basePackages = {
      "io.camunda.operate.property",
      "io.camunda.operate.tenant",
      "io.camunda.operate.connect",
      "io.camunda.operate.store",
      "io.camunda.operate.schema",
      "io.camunda.operate.management",
      "io.camunda.operate.conditions"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import({JacksonConfig.class, Metrics.class})
public class SchemaMigration implements CommandLineRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaMigration.class);
  @Autowired private SchemaStartup schemaStartup;

  public static void main(final String[] args) {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    System.setProperty(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");
    final SpringApplication springApplication = new SpringApplication(SchemaMigration.class);
    springApplication.setWebApplicationType(WebApplicationType.NONE);
    springApplication.setAddCommandLineProperties(true);
    springApplication.addListeners(new ApplicationErrorListener());
    final ConfigurableApplicationContext ctx = springApplication.run(args);
    SpringApplication.exit(ctx);
  }

  @Override
  public void run(final String... args) {
    LOGGER.info("SchemaMigration finished.");
  }

  public static class ApplicationErrorListener
      implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(final ApplicationFailedEvent event) {
      if (event.getException() != null) {
        event.getApplicationContext().close();
        System.exit(-1);
      }
    }
  }
}

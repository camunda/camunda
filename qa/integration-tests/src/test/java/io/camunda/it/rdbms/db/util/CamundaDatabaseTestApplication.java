/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import io.camunda.db.rdbms.RdbmsService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;

public final class CamundaDatabaseTestApplication
    implements ExtensionContext.Store.CloseableResource {

  private final CamundaDatabaseTestApplicationBuilder applicationBuilder;
  private ConfigurableApplicationContext springContext;

  private GenericContainer<?> databaseContainer;

  private CamundaDatabaseTestApplication(
      final CamundaDatabaseTestApplicationBuilder applicationBuilder) {
    this.applicationBuilder = applicationBuilder;
  }

  public static CamundaDatabaseTestApplicationBuilder builder() {
    return new CamundaDatabaseTestApplicationBuilder();
  }

  public CamundaDatabaseTestApplication withDatabaseContainer(
      final GenericContainer<?> databaseContainer) {
    this.databaseContainer = databaseContainer;
    return this;
  }

  public void start() {
    databaseContainer.start();

    final SpringApplication application;
    if (databaseContainer instanceof final JdbcDatabaseContainer<?> jdbcDatabaseContainer) {
      application =
          applicationBuilder
              .withProperty("spring.datasource.url", jdbcDatabaseContainer.getJdbcUrl())
              .withProperty("spring.datasource.username", jdbcDatabaseContainer.getUsername())
              .withProperty("spring.datasource.password", jdbcDatabaseContainer.getPassword())
              .withProperty("spring.datasource.password", jdbcDatabaseContainer.getPassword())
              .withProperty("logging.level.io.camunda.db.rdbms", "DEBUG")
              .withProperty("logging.level.org.mybatis", "DEBUG")
              .buildApplication();
    } else {
      application = applicationBuilder.buildApplication();
    }

    springContext = application.run();
  }

  @Override
  public void close() {
    springContext.stop();
    databaseContainer.close();
  }

  public RdbmsService getRdbmsService() {
    return springContext.getBean(RdbmsService.class);
  }

  public static class CamundaDatabaseTestApplicationBuilder {

    private final SpringApplicationBuilder applicationBuilder;

    private Class<?>[] configurations;
    private final Map<String, Object> properties =
        new HashMap<>(
            Map.of(
                "spring.liquibase.enabled", "false",
                "camunda.database.type", "rdbms"));

    public CamundaDatabaseTestApplicationBuilder() {
      applicationBuilder =
          new SpringApplicationBuilder()
              .web(WebApplicationType.NONE)
              .logStartupInfo(true)
              .bannerMode(Mode.OFF)
              .registerShutdownHook(false);
    }

    public CamundaDatabaseTestApplicationBuilder withConfiguration(
        final Class<?>... configurations) {
      this.configurations = configurations;
      return this;
    }

    public CamundaDatabaseTestApplicationBuilder withProperty(
        final String propertyKey, final String value) {
      properties.put(propertyKey, value);
      return this;
    }

    public CamundaDatabaseTestApplication build() {
      return new CamundaDatabaseTestApplication(this);
    }

    protected SpringApplication buildApplication() {
      return applicationBuilder.sources(configurations).properties(properties).build();
    }
  }
}

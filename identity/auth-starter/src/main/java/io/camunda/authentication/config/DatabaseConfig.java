/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {
  @Bean
  public DataSource dataSource(final DataSourceProperties dataSourceProperties) {
    final var datasource =
        DataSourceBuilder.create()
            .url(dataSourceProperties.getUrl())
            .username(dataSourceProperties.getUsername())
            .password(dataSourceProperties.getPassword())
            .build();

    return datasource;
  }
}

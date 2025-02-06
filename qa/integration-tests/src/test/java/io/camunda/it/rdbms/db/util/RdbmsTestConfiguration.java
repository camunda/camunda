/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableAutoConfiguration
@Import({RdbmsConfiguration.class})
public class RdbmsTestConfiguration {

  @Bean
  public PlatformTransactionManager platformTransactionManager(final DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean
  public MeterRegistry meterRegistry() {
    return Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS);
  }
}

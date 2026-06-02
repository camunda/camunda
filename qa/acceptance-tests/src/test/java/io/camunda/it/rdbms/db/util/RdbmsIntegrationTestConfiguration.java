/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.write.queue.TransactionRunner;
import io.micrometer.core.instrument.MeterRegistry;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

/**
 * Test configuration that forces {@code TransactionRunner.noop()} regardless of whether Spring
 * provides a {@link org.springframework.transaction.PlatformTransactionManager}. This matches the
 * production deployment where no Spring transaction manager is available, meaning MyBatis manages
 * its own JDBC transactions inside {@code DefaultExecutionQueue.doFLush()} via {@code
 * session.commit()} and {@code session.rollback()}.
 *
 * <p>This configuration relies on {@code RdbmsDataSources.buildDataSource()} setting {@code
 * autoCommit=false} on the HikariCP pool so that {@code SpringManagedTransaction} performs real
 * JDBC commit and rollback operations rather than silently skipping them.
 */
@Configuration
@EnableAutoConfiguration
@Import({RdbmsConfiguration.class, UnifiedConfigurationModule.class})
@PropertySource("classpath:rdbms-test-defaults.properties")
public class RdbmsIntegrationTestConfiguration {

  /**
   * Overrides the {@code TransactionRunner} bean produced by {@link RdbmsConfiguration} with a
   * no-op implementation. The {@code @Primary} annotation ensures this bean wins over any
   * auto-configured or {@link RdbmsConfiguration}-provided runner.
   */
  @Bean
  @Primary
  public TransactionRunner noopTransactionRunner() {
    return TransactionRunner.noop();
  }

  @Bean
  public MeterRegistry meterRegistry() {
    return Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS);
  }
}

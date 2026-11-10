/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.zeebe.broker.logstreams.rdbms.LogBlockRepository;
import io.camunda.zeebe.broker.logstreams.rdbms.MyBatisLogBlockRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Experimental: stores the partitions' logs in the RDBMS secondary storage instead of Atomix, when
 * {@code camunda.experimental.rdbms-log-storage.enabled} is true.
 *
 * <p>The log tables are created with the rest of the RDBMS schema, which happens after the broker
 * has started. Each repository therefore reports whether its tenant's schema is ready, and the
 * partitions wait for it before installing their log storage, as the RDBMS exporter does.
 */
@Configuration(proxyBeanMethods = false)
@Import(RdbmsConfiguration.class)
@ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
@ConditionalOnProperty(
    name = "camunda.experimental.rdbms-log-storage.enabled",
    havingValue = "true")
public class RdbmsLogStorageConfiguration {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  /**
   * Builds the repositories on the tenants' existing MyBatis session factories, which load the log
   * mapper with all others and supply the tenant's table prefix.
   */
  @Bean
  public RdbmsLogBlockRepositories rdbmsLogBlockRepositories(
      final Map<String, SqlSessionFactory> sqlSessionFactories,
      final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry) {
    final Map<String, IntFunction<LogBlockRepository>> factories = new LinkedHashMap<>();
    sqlSessionFactories.forEach(
        (physicalTenantId, sqlSessionFactory) -> {
          LOGGER.info(
              "Storing the partition logs of physical tenant '{}' in the RDBMS", physicalTenantId);
          factories.put(
              physicalTenantId,
              partitionId ->
                  new MyBatisLogBlockRepository(
                      sqlSessionFactory,
                      partitionId,
                      () -> rdbmsSchemaManagerRegistry.isInitialized(physicalTenantId)));
        });
    return new RdbmsLogBlockRepositories(Map.copyOf(factories));
  }

  /**
   * Per physical tenant, creates the repository a partition stores its log in. A type of its own,
   * since Spring would fill a bare {@code Map} dependency from beans of its value type instead.
   */
  public record RdbmsLogBlockRepositories(
      Map<String, IntFunction<LogBlockRepository>> byPhysicalTenant) {}
}

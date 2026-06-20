/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.application.commons.search.PhysicalTenantResourceAccessControllers;
import io.camunda.application.commons.search.PhysicalTenantSearchClientReaders;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.RdbmsTenantReaders;
import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProviderFactory;
import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.RdbmsTableRowCountMetrics;
import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.security.core.authz.ResourceAccessController;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
@Import(MyBatisConfiguration.class)
public class RdbmsConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsConfiguration.class);

  @Bean
  public RdbmsTableRowCountMetrics rdbmsTableRowCountMetrics(
      final TableMetricsMapper tableMetricsMapper, final Camunda configuration) {
    final var metricsConfig = configuration.getData().getSecondaryStorage().getRdbms().getMetrics();
    return new RdbmsTableRowCountMetrics(
        tableMetricsMapper, metricsConfig.getTableRowCountCacheDuration());
  }

  @Bean
  public PersistentWebSessionDbReader persistentWebSessionReader(
      final PersistentWebSessionMapper persistentWebSessionMapper) {
    return new PersistentWebSessionDbReader(persistentWebSessionMapper);
  }

  @Bean
  public PersistentWebSessionWriter persistentWebSessionWriter(
      final PersistentWebSessionMapper persistentWebSessionMapper) {
    return new PersistentWebSessionWriter(persistentWebSessionMapper);
  }

  @Bean
  public ReplicationLogStatusProviderFactory replicationLogStatusProviderFactory(
      final RdbmsDataSources rdbmsDataSources,
      final ReplicationStatusMapper replicationStatusMapper) {
    return new ReplicationLogStatusProviderFactory(
        rdbmsDataSources.vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID), replicationStatusMapper);
  }

  @Bean
  public RdbmsWriterFactory rdbmsWriterFactory(
      final Map<String, RdbmsMapperBundle> rdbmsMapperBundles, final MeterRegistry meterRegistry) {
    return new RdbmsWriterFactory(rdbmsMapperBundles, meterRegistry);
  }

  @Bean
  Map<String, RdbmsTenantReaders> rdbmsTenantReaders(
      final Map<String, RdbmsMapperBundle> rdbmsMapperBundles,
      final PhysicalTenantResolver physicalTenantResolver) {
    final var byTenant = new LinkedHashMap<String, RdbmsTenantReaders>();
    rdbmsMapperBundles.forEach(
        (tenantId, bundle) ->
            byTenant.put(
                tenantId,
                RdbmsTenantReaders.create(
                    bundle,
                    physicalTenantResolver
                        .forPhysicalTenant(tenantId)
                        .getData()
                        .getSecondaryStorage()
                        .getRdbms()
                        .getQuery()
                        .toReaderConfig())));
    return Map.copyOf(byTenant);
  }

  @Bean
  public PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders(
      final Map<String, RdbmsTenantReaders> rdbmsTenantReaders) {
    final var byTenant = new LinkedHashMap<String, SearchClientReaders>();
    rdbmsTenantReaders.forEach(
        (tenantId, readers) -> byTenant.put(tenantId, readers.toSearchClientReaders()));
    return new PhysicalTenantSearchClientReaders(Map.copyOf(byTenant));
  }

  @Bean
  public CamundaSearchClients camundaSearchClients(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
      final Optional<PhysicalTenantResourceAccessControllers>
          physicalTenantResourceAccessControllers) {
    return new CamundaSearchClients(
        physicalTenantSearchClientReaders.readersByPhysicalTenant(),
        physicalTenantResourceAccessControllers
            .map(PhysicalTenantResourceAccessControllers::controllersByPhysicalTenant)
            .orElseGet(() -> failFastControllers(physicalTenantSearchClientReaders)));
  }

  /**
   * Fallback for non-web contexts (e.g. Restore, engine-only integration tests) where the per-PT
   * {@link PhysicalTenantResourceAccessControllers} bean is not created. Such contexts do not
   * perform authorized data-plane reads; an empty delegating controller keeps the context startable
   * while failing fast on any accidental read.
   */
  private static Map<String, ResourceAccessController> failFastControllers(
      final PhysicalTenantSearchClientReaders readers) {
    return readers.readersByPhysicalTenant().keySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                tenantId -> tenantId,
                tenantId -> new ResourceAccessDelegatingController(List.of())));
  }

  @Bean
  public AuthorizationReader authorizationReader(
      final Map<String, RdbmsTenantReaders> rdbmsTenantReaders) {
    return defaultReaders(rdbmsTenantReaders).authorizationReader();
  }

  @Bean
  public RdbmsService rdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final Map<String, RdbmsTenantReaders> rdbmsTenantReaders,
      final ReplicationLogStatusProviderFactory replicationLogStatusProviderFactory) {
    return new RdbmsService(
        rdbmsWriterFactory, rdbmsTenantReaders, replicationLogStatusProviderFactory);
  }

  @Bean
  public CommandLineRunner logJdbcDriverInfo(final RdbmsDataSources rdbmsDataSources) {
    return args -> {
      if (LOG.isDebugEnabled()) {
        rdbmsDataSources
            .dataSources()
            .forEach(
                (physicalTenantId, datasource) -> {
                  try (final Connection conn = datasource.getConnection()) {
                    final DatabaseMetaData meta = conn.getMetaData();
                    LOG.debug(
                        "JDBC Driver [physicalTenantId={}]: {} {}",
                        physicalTenantId,
                        meta.getDriverName(),
                        meta.getDriverVersion());
                    LOG.debug(
                        "JDBC Spec [physicalTenantId={}]: {}.{}",
                        physicalTenantId,
                        meta.getJDBCMajorVersion(),
                        meta.getJDBCMinorVersion());
                  } catch (final SQLException e) {
                    // Best-effort diagnostics only: a failure for one physical tenant must not
                    // abort
                    // application startup (this runs as a CommandLineRunner).
                    LOG.debug(
                        "Could not log JDBC driver info for physical tenant {}",
                        physicalTenantId,
                        e);
                  }
                });
      }
    };
  }

  @Bean
  HealthContributor rdbmsStatusHealthIndicator(final RdbmsDataSources rdbmsDataSources) {
    // Equivalent to what Boot would normally wire for "db"
    // TODO: make this a CompositeHealthContributor over all physical tenants (one
    // DataSourceHealthIndicator per pool) instead of default-tenant only.
    return new DataSourceHealthIndicator(
        rdbmsDataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID));
  }

  private static RdbmsTenantReaders defaultReaders(
      final Map<String, RdbmsTenantReaders> rdbmsTenantReaders) {
    final var defaults = rdbmsTenantReaders.get(DEFAULT_PHYSICAL_TENANT_ID);
    if (defaults == null) {
      throw new IllegalStateException(
          "Missing default physical tenant '%s' in rdbmsTenantReaders; known tenants: %s"
              .formatted(DEFAULT_PHYSICAL_TENANT_ID, rdbmsTenantReaders.keySet()));
    }
    return defaults;
  }
}

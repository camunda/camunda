/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.zeebe.protocol.impl.record.RecordMetadata.CURRENT_BROKER_VERSION;

import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.application.commons.search.PhysicalTenantSearchEngineConfigurations;
import io.camunda.application.initializers.StandaloneSchemaManagerInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.ElasticsearchExporterSchemaManager;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterSchemaManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Solely creates or updates the secondary storage schema by running this standalone application.
 *
 * <p>Configure with the unified configuration properties under {@code
 * camunda.data.secondary-storage}, for example:
 *
 * <pre>
 * camunda.data.secondary-storage.type=elasticsearch
 * camunda.data.secondary-storage.elasticsearch.url=
 * camunda.data.secondary-storage.elasticsearch.username=
 * camunda.data.secondary-storage.elasticsearch.password=
 * camunda.data.secondary-storage.elasticsearch.index-prefix=
 * </pre>
 *
 * <p>All of those properties can also be handed over via environment variables, e.g. {@code
 * CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL}. Every property can be overridden per physical
 * tenant under {@code camunda.physical-tenants.<id>.}, in which case each tenant's schema is
 * created against its own storage.
 */
@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneSchemaManager implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneSchemaManager.class);

  private final PhysicalTenantResolver physicalTenantResolver;
  private final LegacyBrokerBasedProperties brokerProperties;
  private final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant;

  public StandaloneSchemaManager(
      final PhysicalTenantResolver physicalTenantResolver,
      final LegacyBrokerBasedProperties brokerProperties,
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant) {
    this.physicalTenantResolver = physicalTenantResolver;
    this.brokerProperties = brokerProperties;
    this.searchEngineConfigurationsByTenant = searchEngineConfigurationsByTenant;
  }

  public static void main(final String[] args) throws IOException {

    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    // show banner
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");

    LOG.info("Creating/updating schema for Camunda ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(UnifiedConfigurationModule.class, StandaloneSchemaManagerConfiguration.class)
        .initializers(new StandaloneSchemaManagerInitializer())
        .addCommandLineProperties(true)
        .listeners(new ApplicationErrorListener())
        .run(args);

    LOG.info("... finished creating/updating schema for Camunda");

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  /**
   * Creates the schema for every physical tenant, one tenant at a time.
   *
   * <p>Every tenant is attempted even after an earlier one failed, and the failures are reported
   * together at the end: an operator rolling out several tenants needs to see all of the broken
   * ones from a single run rather than discovering them one invocation at a time.
   *
   * <p>A tenant that succeeded keeps its schema when a later one fails. Nothing is rolled back,
   * because schema creation is idempotent and re-runnable: after fixing the failing tenant, running
   * the CLI again is the repair.
   */
  @Override
  public void run(final String... args) throws Exception {
    final var tenantIds = searchEngineConfigurationsByTenant.keySet();

    LOG.info("Creating/updating schema for {} physical tenant(s): {}", tenantIds.size(), tenantIds);

    final Map<String, Exception> failures = new LinkedHashMap<>();
    for (final String tenantId : tenantIds) {
      try {
        createWebappIndices(tenantId);
        createZeebeIndices(tenantId);
        LOG.info("Created/updated schema for physical tenant '{}'.", tenantId);
      } catch (final Exception e) {
        LOG.error("Failed to create/update schema for physical tenant '{}'.", tenantId, e);
        failures.put(tenantId, e);
      }
    }

    if (!failures.isEmpty()) {
      final var succeeded = new ArrayList<>(tenantIds);
      succeeded.removeAll(failures.keySet());
      throw new SchemaCreationFailedException(failures, succeeded);
    }
  }

  /**
   * Creates the harmonized indices and templates that the webapps and the Camunda exporter read and
   * write, via {@link SchemaManager}.
   */
  private void createWebappIndices(final String tenantId) throws IOException {
    final SearchEngineConfiguration configuration =
        searchEngineConfigurationsByTenant.get(tenantId);
    final IndexDescriptors indexDescriptors = indexDescriptorsFor(configuration);

    try (final ClientAdapter clientAdapter = ClientAdapter.of(configuration.connect())) {
      try (final SchemaManager schemaManager =
          new SchemaManager(
              clientAdapter.getSearchEngineClient(),
              indexDescriptors.indices(),
              indexDescriptors.templates(),
              configuration,
              clientAdapter.objectMapper())) {
        schemaManager.startupOnce();
      }
    }
  }

  private void createZeebeIndices(final String tenantId) {
    final Map<String, ExporterCfg> exporters = exportersOf(tenantId);
    LOG.info(
        "Creating/updating Zeebe record schema for physical tenant '{}' from exporter(s) {}.",
        tenantId,
        exporters.keySet());
    for (final SchemaCreator schemaCreator : zeebeSchemaCreators(exporters)) {
      schemaCreator.createSchema(CURRENT_BROKER_VERSION.toString());
    }
  }

  private static IndexDescriptors indexDescriptorsFor(
      final SearchEngineConfiguration configuration) {
    final var connect = configuration.connect();
    return new IndexDescriptors(connect.getIndexPrefix(), connect.getTypeEnum().isElasticSearch());
  }

  /**
   * A tenant's exporter definitions. The default tenant reads {@link LegacyBrokerBasedProperties}
   * directly to keep the legacy {@code zeebe.broker.exporters.*} namespace working.
   */
  private Map<String, ExporterCfg> exportersOf(final String tenantId) {
    if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
      return brokerProperties.getExporters();
    }
    return BrokerBasedPropertiesOverride.convert(physicalTenantResolver.forPhysicalTenant(tenantId))
        .getExporters();
  }

  private List<SchemaCreator> zeebeSchemaCreators(final Map<String, ExporterCfg> exporters) {
    final SchemaManagerFactory schemaManagerFactory = getSchemaManagerFactory();
    return exporters.entrySet().stream()
        .map(entry -> schemaManagerFactory.create(entry.getKey(), entry.getValue()))
        .toList();
  }

  private SchemaManagerFactory getSchemaManagerFactory() {
    return (id, cfg) -> {
      if (ElasticsearchExporter.class.getName().equals(cfg.getClassName())) {
        final var config =
            new ExporterConfiguration(id, cfg.getArgs())
                .instantiate(ElasticsearchExporterConfiguration.class);
        final var schemaManager = new ElasticsearchExporterSchemaManager(config);
        return schemaManager::createSchema;
      } else if (OpensearchExporter.class.getName().equals(cfg.getClassName())) {
        final var config =
            new ExporterConfiguration(id, cfg.getArgs())
                .instantiate(OpensearchExporterConfiguration.class);
        final var schemaManager = new OpensearchExporterSchemaManager(config);
        return schemaManager::createSchema;
      } else {
        return brokerVersion -> {};
      }
    };
  }

  @EnableAutoConfiguration
  @EnableConfigurationProperties(LegacyBrokerBasedProperties.class)
  @Import(PhysicalTenantSearchEngineConfigurations.class)
  public static class StandaloneSchemaManagerConfiguration {}

  /**
   * Names the tenants that failed, so the cause is visible without reading back through the log.
   */
  private static final class SchemaCreationFailedException extends RuntimeException {
    private SchemaCreationFailedException(
        final Map<String, Exception> failures, final List<String> succeeded) {
      super(
          "Failed to create/update the schema for %d of %d physical tenant(s): %s. Succeeded: %s. "
                  .formatted(
                      failures.size(),
                      failures.size() + succeeded.size(),
                      failures.keySet(),
                      succeeded.isEmpty() ? "none" : succeeded)
              + "Schema creation is idempotent, so re-run once the reported tenants are fixed.");
      failures.values().forEach(this::addSuppressed);
    }
  }

  /*
   * Since we don't have a common SchemaManager interface that we can put in a common project for
   * Elasticsearch and OpenSearch exporters (yet), we use these functional interfaces to give at least
   * a sense of abstraction.
   */
  @FunctionalInterface
  private interface SchemaCreator {
    void createSchema(String brokerVersion);
  }

  @FunctionalInterface
  private interface SchemaManagerFactory {
    SchemaCreator create(String exporterId, ExporterCfg exporterConfig);
  }
}

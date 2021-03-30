/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema.migration;

import static io.zeebe.tasklist.util.CollectionUtil.filter;

import io.zeebe.tasklist.es.RetryElasticsearchClient;
import io.zeebe.tasklist.exceptions.MigrationException;
import io.zeebe.tasklist.property.MigrationProperties;
import io.zeebe.tasklist.property.TasklistElasticsearchProperties;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.schema.IndexSchemaValidator;
import io.zeebe.tasklist.schema.SemanticVersion;
import io.zeebe.tasklist.schema.indices.IndexDescriptor;
import io.zeebe.tasklist.schema.templates.TemplateDescriptor;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Migrates an Tasklist schema from one version to another. Requires an already created destination
 * schema provided by a schema manager.
 *
 * <p>Tries to detect source/previous schema if not provided.
 */
@Component
@Configuration
public class Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Migrator.class);

  private static final String REFRESH_INTERVAL = "index.refresh_interval";
  private static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";

  @Autowired private List<IndexDescriptor> indexDescriptors;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired private StepsRepository stepsRepository;

  @Autowired private MigrationProperties migrationProperties;

  @Autowired
  @Qualifier("migrationThreadPoolExecutor")
  private ThreadPoolTaskExecutor migrationExecutor;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Bean("migrationThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(migrationProperties.getThreadsCount());
    executor.setMaxPoolSize(migrationProperties.getThreadsCount());
    executor.setThreadNamePrefix("migration_");
    executor.initialize();
    return executor;
  }

  public void migrate() throws MigrationException {
    try {
      stepsRepository.updateSteps();
    } catch (IOException e) {
      throw new MigrationException(
          String.format("Migration failed in updating steps: %s ", e.getMessage()));
    }
    boolean failed = false;
    final List<Future<Boolean>> results =
        indexDescriptors.stream().map(this::migrateIndexInThread).collect(Collectors.toList());
    for (Future<Boolean> result : results) {
      try {
        if (!result.get()) {
          failed = true;
        }
      } catch (Exception e) {
        LOGGER.error("Migration failed: ", e);
        failed = true;
      }
    }
    if (failed) {
      throw new MigrationException("Migration failed. See logging messages above.");
    }
  }

  private Future<Boolean> migrateIndexInThread(IndexDescriptor indexDescriptor) {
    return migrationExecutor.submit(
        () -> {
          try {
            migrateIndexIfNecessary(indexDescriptor);
          } catch (Exception e) {
            LOGGER.error("Migration for {} failed:", indexDescriptor.getIndexName(), e);
            return false;
          }
          return true;
        });
  }

  private void migrateIndexIfNecessary(IndexDescriptor indexDescriptor)
      throws MigrationException, IOException {
    LOGGER.info("Check if index {} needs to migrate.", indexDescriptor.getIndexName());
    final Set<String> olderVersions = indexSchemaValidator.olderVersionsForIndex(indexDescriptor);
    if (olderVersions.size() > 1) {
      throw new MigrationException(
          String.format(
              "For index %s are existing more than one older versions: %s ",
              indexDescriptor.getIndexName(), olderVersions));
    }
    if (olderVersions.isEmpty()) {
      LOGGER.info(
          "No migration needed for {}, no previous indices found.", indexDescriptor.getIndexName());
    } else {
      final String olderVersion = olderVersions.iterator().next();
      final String currentVersion = indexDescriptor.getVersion();
      final List<Step> stepsForIndex =
          stepsRepository.findNotAppliedFor(indexDescriptor.getIndexName());
      final Plan plan =
          createPlanFor(
              indexDescriptor.getIndexName(), olderVersion, currentVersion, stepsForIndex);
      migrateIndex(indexDescriptor, plan);
      if (migrationProperties.isDeleteSrcSchema()) {
        final String olderBaseIndexName =
            String.format(
                "%s-%s-%s_",
                tasklistProperties.getElasticsearch().getIndexPrefix(),
                indexDescriptor.getIndexName(),
                olderVersion);
        final String deleteIndexPattern = String.format("%s*", olderBaseIndexName);
        LOGGER.info("Deleted previous indices for pattern {}", deleteIndexPattern);
        retryElasticsearchClient.deleteIndicesFor(deleteIndexPattern);
        if (indexDescriptor instanceof TemplateDescriptor) {
          final String deleteTemplatePattern = String.format("%stemplate", olderBaseIndexName);
          LOGGER.info("Deleted previous templates for {}", deleteTemplatePattern);
          retryElasticsearchClient.deleteTemplatesFor(deleteTemplatePattern);
        }
      }
    }
  }

  private void migrateIndex(final IndexDescriptor indexDescriptor, final Plan plan)
      throws IOException, MigrationException {
    LOGGER.debug("Save current settings for {}", indexDescriptor.getFullQualifiedName());
    final Map<String, String> indexSettings =
        retryElasticsearchClient.getIndexSettingsFor(
            indexDescriptor.getFullQualifiedName(), NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    LOGGER.debug("Set reindex settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder().put(NUMBERS_OF_REPLICA, "0").put(REFRESH_INTERVAL, "-1").build(),
        indexDescriptor.getDerivedIndexNamePattern());

    LOGGER.info("Execute plan: {} ", plan);
    plan.executeOn(retryElasticsearchClient);

    LOGGER.debug("Save applied steps in migration repository");
    for (final Step step : plan.getSteps()) {
      step.setApplied(true).setAppliedDate(OffsetDateTime.now());
      stepsRepository.save(step);
    }

    LOGGER.debug("Restore settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    final String numberOfReplicas =
        indexSettings.getOrDefault(NUMBERS_OF_REPLICA, "" + elsConfig.getNumberOfReplicas());
    final String refreshInterval =
        indexSettings.getOrDefault(REFRESH_INTERVAL, elsConfig.getRefreshInterval());
    retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder()
            .put(NUMBERS_OF_REPLICA, numberOfReplicas)
            .put(REFRESH_INTERVAL, refreshInterval)
            .build(),
        indexDescriptor.getDerivedIndexNamePattern());

    LOGGER.info("Refresh index {}", indexDescriptor.getDerivedIndexNamePattern());
    retryElasticsearchClient.refresh(indexDescriptor.getDerivedIndexNamePattern());
  }

  protected Plan createPlanFor(
      final String indexName,
      final String srcVersion,
      final String dstVersion,
      final List<Step> steps) {
    final SemanticVersion sourceVersion = SemanticVersion.fromVersion(srcVersion);
    final SemanticVersion destinationVersion = SemanticVersion.fromVersion(dstVersion);

    final List<Step> sortByVersion = new ArrayList<>(steps);
    sortByVersion.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);

    final List<Step> onlyAffectedVersions =
        filter(
            sortByVersion,
            s ->
                SemanticVersion.fromVersion(s.getVersion())
                    .isBetween(sourceVersion, destinationVersion));

    final String indexPrefix = tasklistProperties.getElasticsearch().getIndexPrefix();
    final String srcIndex = String.format("%s-%s-%s", indexPrefix, indexName, srcVersion);
    final String dstIndex = String.format("%s-%s-%s", indexPrefix, indexName, dstVersion);

    return Plan.forReindex()
        .setBatchSize(migrationProperties.getReindexBatchSize())
        .setSlices(migrationProperties.getSlices())
        .setSrcIndex(srcIndex)
        .setDstIndex(dstIndex)
        .setSteps(onlyAffectedVersions);
  }
}

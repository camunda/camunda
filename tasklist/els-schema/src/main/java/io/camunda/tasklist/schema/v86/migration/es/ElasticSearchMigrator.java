/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration.es;

import static io.camunda.tasklist.es.RetryElasticsearchClient.NO_REFRESH;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NO_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.REFRESH_INTERVAL;
import static io.camunda.tasklist.schema.v86.indices.AbstractIndexDescriptor.formatFullQualifiedIndexName;
import static io.camunda.tasklist.util.CollectionUtil.filter;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.property.MigrationProperties;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.IndexSchemaValidator;
import io.camunda.tasklist.schema.v86.SemanticVersion;
import io.camunda.tasklist.schema.v86.migration.Migrator;
import io.camunda.tasklist.schema.v86.migration.Step;
import io.camunda.tasklist.schema.v86.migration.StepsRepository;
import io.camunda.tasklist.schema.v86.templates.TemplateDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchMigrator implements Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMigrator.class);

  @Autowired private List<IndexDescriptor> indexDescriptors;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired private StepsRepository stepsRepository;

  @Autowired private MigrationProperties migrationProperties;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Bean("tasklistMigrationThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(migrationProperties.getThreadsCount());
    executor.setMaxPoolSize(migrationProperties.getThreadsCount());
    executor.setThreadNamePrefix("migration_");
    executor.initialize();
    return executor;
  }

  @Override
  public void migrate() throws MigrationException {
    try {
      stepsRepository.updateSteps();
    } catch (final IOException e) {
      throw new MigrationException(
          String.format("Migration failed in updating steps: %s ", e.getMessage()));
    }
    boolean failed = false;
    final List<Future<Boolean>> results =
        indexDescriptors.stream().map(this::migrateIndexInThread).toList();
    for (final Future<Boolean> result : results) {
      try {
        if (!result.get()) {
          failed = true;
        }
      } catch (final Exception e) {
        LOGGER.error("Migration failed: ", e);
        failed = true;
      }
    }
    getTaskExecutor().shutdown();
    if (failed) {
      throw new MigrationException("Migration failed. See logging messages above.");
    }
  }

  private Future<Boolean> migrateIndexInThread(final IndexDescriptor indexDescriptor) {
    return getTaskExecutor()
        .submit(
            () -> {
              try {
                migrateIndexIfNecessary(indexDescriptor);
              } catch (final Exception e) {
                LOGGER.error("Migration for {} failed:", indexDescriptor.getIndexName(), e);
                return false;
              }
              return true;
            });
  }

  private void migrateIndexIfNecessary(final IndexDescriptor indexDescriptor)
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
      final ReindexPlanElasticSearch plan =
          createPlanFor(
              indexDescriptor.getIndexName(), olderVersion, currentVersion, stepsForIndex);
      migrateIndex(indexDescriptor, plan);
      if (migrationProperties.isDeleteSrcSchema()) {
        final String olderBaseIndexName =
            formatFullQualifiedIndexName(
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

  private void migrateIndex(
      final IndexDescriptor indexDescriptor, final ReindexPlanElasticSearch plan)
      throws IOException, MigrationException {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();

    LOGGER.debug("Save current settings for {}", indexDescriptor.getFullQualifiedName());
    final Map<String, String> indexSettings =
        getIndexSettingsOrDefaultsFor(indexDescriptor, elsConfig);

    LOGGER.debug("Set reindex settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder()
            .put(NUMBERS_OF_REPLICA, NO_REPLICA)
            .put(REFRESH_INTERVAL, NO_REFRESH)
            .build(),
        indexDescriptor.getDerivedIndexNamePattern());

    LOGGER.info("Execute plan: {} ", plan);
    plan.executeOn(retryElasticsearchClient);

    LOGGER.debug("Save applied steps in migration repository");
    for (final Step step : plan.getSteps()) {
      step.setApplied(true).setAppliedDate(OffsetDateTime.now());
      stepsRepository.save(step);
    }

    LOGGER.debug("Restore settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder()
            .put(NUMBERS_OF_REPLICA, indexSettings.get(NUMBERS_OF_REPLICA))
            .put(REFRESH_INTERVAL, indexSettings.get(REFRESH_INTERVAL))
            .build(),
        indexDescriptor.getDerivedIndexNamePattern());

    LOGGER.info("Refresh index {}", indexDescriptor.getDerivedIndexNamePattern());
    retryElasticsearchClient.refresh(indexDescriptor.getDerivedIndexNamePattern());
  }

  private Map<String, String> getIndexSettingsOrDefaultsFor(
      final IndexDescriptor indexDescriptor, final TasklistElasticsearchProperties elsConfig) {
    final Map<String, String> settings = new HashMap<>();
    settings.put(
        REFRESH_INTERVAL,
        retryElasticsearchClient.getOrDefaultRefreshInterval(
            indexDescriptor.getFullQualifiedName(), elsConfig.getRefreshInterval()));
    settings.put(
        NUMBERS_OF_REPLICA,
        retryElasticsearchClient.getOrDefaultNumbersOfReplica(
            indexDescriptor.getFullQualifiedName(), "" + elsConfig.getNumberOfReplicas()));
    return settings;
  }

  protected ReindexPlanElasticSearch createPlanFor(
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
    final String srcIndex = formatFullQualifiedIndexName(indexPrefix, indexName, srcVersion);
    final String dstIndex = formatFullQualifiedIndexName(indexPrefix, indexName, dstVersion);

    return ReindexPlanElasticSearch.create()
        .setBatchSize(migrationProperties.getReindexBatchSize())
        .setSlices(migrationProperties.getSlices())
        .setSrcIndex(srcIndex)
        .setDstIndex(dstIndex)
        .setSteps(onlyAffectedVersions);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import static io.camunda.operate.schema.SchemaManager.*;
import static io.camunda.operate.util.CollectionUtil.filter;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexSchemaValidator;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Migrates an operate schema from one version to another. Requires an already created destination
 * schema provided by a schema manager. Tries to detect source/previous schema if not provided.
 */
@Component
@Configuration
public class Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Migrator.class);

  @Autowired private List<IndexDescriptor> indexDescriptors;

  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired private OperateProperties operateProperties;

  @Autowired private SchemaManager schemaManager;

  @Autowired private StepsRepository stepsRepository;

  @Autowired private MigrationProperties migrationProperties;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Autowired private MigrationPlanFactory migrationPlanFactory;

  @Bean("migrationThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(migrationProperties.getThreadsCount());
    executor.setMaxPoolSize(migrationProperties.getThreadsCount());
    executor.setThreadNamePrefix("migration_");
    executor.initialize();
    return executor;
  }

  public void migrateData() throws MigrationException {
    try {
      stepsRepository.updateSteps();
    } catch (final IOException e) {
      throw new MigrationException(String.format("Migration failed due to %s", e.getMessage()));
    }
    boolean failed = false;
    final List<Future<Boolean>> results =
        indexDescriptors.stream().map(this::migrateIndexInThread).collect(Collectors.toList());
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
    final String currentVersion = indexDescriptor.getVersion();
    if (olderVersions.isEmpty()) {
      // find data initializer steps
      final List<Step> stepsForIndex =
          stepsRepository.findNotAppliedFor(indexDescriptor.getIndexName()).stream()
              .filter(s -> s instanceof DataInitializerStep)
              .collect(Collectors.toList());
      if (stepsForIndex.size() > 0) {
        final Plan plan =
            createPlanFor(indexDescriptor.getIndexName(), "1.0.0", currentVersion, stepsForIndex);
        migrateIndex(indexDescriptor, plan);
      } else {
        LOGGER.info(
            "No migration needed for {}, no previous indices found and no data initializer.",
            indexDescriptor.getIndexName());
      }
    } else {
      final String olderVersion = olderVersions.iterator().next();
      final List<Step> stepsForIndex =
          stepsRepository.findNotAppliedFor(indexDescriptor.getIndexName());
      final Plan plan =
          createPlanFor(
              indexDescriptor.getIndexName(), olderVersion, currentVersion, stepsForIndex);
      migrateIndex(indexDescriptor, plan);
      final var indexPrefix =
          DatabaseInfo.isOpensearch()
              ? operateProperties.getOpensearch().getIndexPrefix()
              : operateProperties.getElasticsearch().getIndexPrefix();
      if (migrationProperties.isDeleteSrcSchema()) {
        final String olderBaseIndexName =
            String.format("%s-%s-%s_", indexPrefix, indexDescriptor.getIndexName(), olderVersion);
        final String deleteIndexPattern = String.format("%s*", olderBaseIndexName);
        LOGGER.info("Deleted previous indices for pattern {}", deleteIndexPattern);
        schemaManager.deleteIndicesFor(deleteIndexPattern);
        if (indexDescriptor instanceof IndexTemplateDescriptor) {
          final String deleteTemplatePattern = String.format("%stemplate", olderBaseIndexName);
          LOGGER.info("Deleted previous templates for {}", deleteTemplatePattern);
          schemaManager.deleteTemplatesFor(deleteTemplatePattern);
        }
      }
    }
  }

  public void migrateIndex(final IndexDescriptor indexDescriptor, final Plan plan)
      throws IOException, MigrationException {
    final String refreshInterval;
    final Integer numberOfReplicas;
    if (DatabaseInfo.isOpensearch()) {
      refreshInterval = operateProperties.getOpensearch().getRefreshInterval();
      numberOfReplicas = operateProperties.getOpensearch().getNumberOfReplicas();
    } else {
      refreshInterval = operateProperties.getElasticsearch().getRefreshInterval();
      numberOfReplicas = operateProperties.getElasticsearch().getNumberOfReplicas();
    }

    LOGGER.debug("Save current settings for {}", indexDescriptor.getFullQualifiedName());
    final Map<String, String> indexSettings =
        getIndexSettingsOrDefaultsFor(indexDescriptor, refreshInterval, numberOfReplicas);

    LOGGER.debug("Set reindex settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    schemaManager.setIndexSettingsFor(
        Map.of(
            NUMBERS_OF_REPLICA,
            indexSettings.get(NUMBERS_OF_REPLICA),
            REFRESH_INTERVAL,
            NO_REFRESH),
        indexDescriptor.getDerivedIndexNamePattern());

    LOGGER.info("Execute plan: {} ", plan);
    plan.executeOn(schemaManager);

    LOGGER.debug("Save applied steps in migration repository");
    for (final Step step : plan.getSteps()) {
      step.setApplied(true).setAppliedDate(OffsetDateTime.now());
      stepsRepository.save(step);
    }

    LOGGER.debug("Restore settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    schemaManager.setIndexSettingsFor(
        Map.of(
            NUMBERS_OF_REPLICA, indexSettings.get(NUMBERS_OF_REPLICA),
            REFRESH_INTERVAL, indexSettings.get(REFRESH_INTERVAL)),
        indexDescriptor.getDerivedIndexNamePattern());

    LOGGER.info("Refresh index {}", indexDescriptor.getDerivedIndexNamePattern());
    schemaManager.refresh(indexDescriptor.getDerivedIndexNamePattern());

    plan.validateMigrationResults(schemaManager);
  }

  private Map<String, String> getIndexSettingsOrDefaultsFor(
      final IndexDescriptor indexDescriptor,
      final String refreshInterval,
      final Integer numberOfReplicas) {
    final Map<String, String> settings = new HashMap<>();
    settings.put(
        REFRESH_INTERVAL,
        schemaManager.getOrDefaultRefreshInterval(
            indexDescriptor.getFullQualifiedName(), refreshInterval));
    settings.put(
        NUMBERS_OF_REPLICA,
        schemaManager.getOrDefaultNumbersOfReplica(
            indexDescriptor.getFullQualifiedName(), "" + numberOfReplicas));
    return settings;
  }

  protected Plan createPlanFor(
      final String indexName,
      final String srcVersion,
      final String dstVersion,
      final List<Step> steps)
      throws MigrationException {
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

    final String indexPrefix =
        DatabaseInfo.isOpensearch()
            ? operateProperties.getOpensearch().getIndexPrefix()
            : operateProperties.getElasticsearch().getIndexPrefix();
    final String srcIndex = String.format("%s-%s-%s", indexPrefix, indexName, srcVersion);
    final String dstIndex = String.format("%s-%s-%s", indexPrefix, indexName, dstVersion);

    // forbid migration when migration steps can't be combined
    if (onlyAffectedVersions.stream().anyMatch(s -> s instanceof ProcessorStep)
        && onlyAffectedVersions.stream().anyMatch(s -> s instanceof SetBpmnProcessIdStep)) {
      throw new MigrationException(
          "Migration plan contains steps that can't be applied together. Check your upgrade path.");
    }
    if (onlyAffectedVersions.size() == 0) {
      final ReindexPlan reindexPlan = migrationPlanFactory.createReindexPlan();
      return reindexPlan.setSrcIndex(srcIndex).setDstIndex(dstIndex);
    } else if (onlyAffectedVersions.get(0) instanceof ProcessorStep) {
      final ReindexPlan reindexPlan = migrationPlanFactory.createReindexPlan();
      return reindexPlan.setSrcIndex(srcIndex).setDstIndex(dstIndex).setSteps(onlyAffectedVersions);
    } else if (onlyAffectedVersions.get(0) instanceof SetBpmnProcessIdStep
        && onlyAffectedVersions.size() == 1) {
      // we don't include version in list-view index name, as we can't know which version we have -
      // older or newer
      final String listViewIndexName =
          String.format("%s-%s", indexPrefix, listViewTemplate.getIndexName());
      final ReindexWithQueryAndScriptPlan reindexPlan =
          migrationPlanFactory.createReindexWithQueryAndScriptPlan();
      return reindexPlan
          .setSrcIndex(srcIndex)
          .setDstIndex(dstIndex)
          .setListViewIndexName(listViewIndexName)
          .setSteps(onlyAffectedVersions);
    } else if (onlyAffectedVersions.get(0) instanceof FillPostImporterQueueStep
        && onlyAffectedVersions.size() == 1) {
      final FillPostImporterQueuePlan fillPostImporterQueuePlan =
          migrationPlanFactory.createFillPostImporterQueuePlan();
      return fillPostImporterQueuePlan
          .setListViewIndexName(
              String.format("%s-%s", indexPrefix, listViewTemplate.getIndexName()))
          .setIncidentsIndexName(
              String.format("%s-%s", indexPrefix, incidentTemplate.getIndexName()))
          .setPostImporterQueueIndexName(postImporterQueueTemplate.getFullQualifiedName())
          .setSteps(onlyAffectedVersions);
    } else if ((onlyAffectedVersions.get(0) instanceof SetBpmnProcessIdStep
            || onlyAffectedVersions.get(0) instanceof FillPostImporterQueueStep)
        && onlyAffectedVersions.size() > 1) {
      throw new MigrationException(
          "Unexpected migration plan: only one step of this type must be present: "
              + onlyAffectedVersions.get(0).getClass().getSimpleName());
    } else {
      throw new MigrationException("Unexpected migration plan.");
    }
  }
}

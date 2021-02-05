/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import org.camunda.operate.exceptions.MigrationException;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.MigrationProperties;
import org.camunda.operate.property.OperateElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.indices.MigrationRepositoryIndex;
import org.camunda.operate.schema.templates.TemplateDescriptor;
import org.camunda.operate.es.RetryElasticsearchClient;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.camunda.operate.util.CollectionUtil.filter;
import static org.camunda.operate.util.CollectionUtil.map;

/**
 * Migrates an operate schema from one version to another.
 * Requires an already created destination schema  provided by a schema manager.
 *
 * Tries to detect source/previous schema if not provided.
 *
 */
@Component
public class Migrator{

  private static final Logger logger = LoggerFactory.getLogger(Migrator.class);

  /**
   * Pattern that will be used for schema version detection.
   */
  public static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  private static final String REFRESH_INTERVAL = "index.refresh_interval";
  private static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";

  @Autowired
  private List<IndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  private StepsRepository stepsRepository;

  @Autowired
  private MigrationProperties migrationProperties;

  @PostConstruct
  private void init() {
    logger.debug("Created Migrator for elasticsearch at {}:{} ",operateProperties.getElasticsearch().getHost(),operateProperties.getElasticsearch().getPort());
  }

  public void migrate() {
    migrate(migrationProperties.getSourceVersion(), migrationProperties.getDestinationVersion());
  }

  /**
   * Migrates from source version to destination version
   *
   * If source version is omitted than the the previous schema will be detected
   * If destination version is omitted than the schema version from operate configuration will be used.
   *
   * @param sourceVersion - migrate from version
   * @param destinationVersion - migrate to version
   */
  public void migrate(final String sourceVersion,final String destinationVersion) {
    logger.info("Check whether migration is needed ...");
    final String dst = destinationVersion != null ? destinationVersion : operateProperties.getSchemaVersion();
    try {
      final Optional<String> srcVersion = sourceVersion != null ? Optional.of(sourceVersion) : detectPreviousSchemaVersion();

      if (srcVersion.isPresent()) {
        String src = srcVersion.get();
        logger.info("Detected previous Operate Elasticsearch schema: {}", src);
        migrateFromTo(src, dst);
      } else {
        logger.info("No previous Operate Elasticsearch schema found. No migration needed.");
      }
    } catch(MigrationException me) {
        try {
          deleteSchema(dst);
          throw new OperateRuntimeException(String.format("Error in migration to %s. Deleted schema for version %s", dst, dst),me);
        } catch (IOException e) {
          throw new OperateRuntimeException(String.format("Error in migration to %s. Could NOT delete schema for version %s", dst, dst), me);
        }
    } catch (Exception e) {
        throw new OperateRuntimeException(String.format("Migration to %s failed:", dst),e);
    }
  }
  /**
   * Detects already existing operate schemas, but returns only one.
   * @return an optional String containing the detected schema version.
   */
  public Optional<String> detectPreviousSchemaVersion() throws MigrationException {
    final List<String> indexNames = retryElasticsearchClient.getIndexNamesFor(getIndexPrefix() + "*");
    final List<String> indexNamesWithoutCurrentVersion = filter(indexNames,
        indexName -> !indexName.contains(operateProperties.getSchemaVersion()) && !indexName.contains(stepsRepository.getName())
    );

    if (indexNamesWithoutCurrentVersion.isEmpty()) {
      return Optional.empty();
    } else {
      final Set<String> versions = new HashSet<>();
      for (final String indexName : indexNamesWithoutCurrentVersion) {
        Matcher matcher = VERSION_PATTERN.matcher(indexName);
        if (matcher.matches() && matcher.groupCount() > 0) {
          versions.add(matcher.group(1));
        }
      }
      if (versions.size() > 1) {
        throw new MigrationException(String.format("Found %d Operate Schema versions: %s .Can only upgrade from one.", versions.size(), versions));
      }
      if (versions.size() == 1){
        String previousVersion = versions.iterator().next();
        SemanticVersion currentVersion = SemanticVersion.fromVersion(operateProperties.getSchemaVersion());
        if(currentVersion.isNewerThan(SemanticVersion.fromVersion(previousVersion))) {
          return Optional.of(previousVersion);
        }
      }
      return Optional.empty();
    }
  }

  private void migrateFromTo(final String srcVersion,final String dstVersion) throws IOException, MigrationException {
    // Retrieve index settings for old schema
    Map<String,String> srcSettings = retryElasticsearchClient.getIndexSettingsFor(
        getIndexNameFor(indexDescriptors.get(0).getMainIndexName(), srcVersion),
        NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
    // Set settings for Reindex
    setSettingsForReindex(dstVersion);

    List<String> indexNames = map(indexDescriptors, IndexDescriptor::getMainIndexName);
    indexNames.addAll(map(templateDescriptors, TemplateDescriptor::getIndexNameFormat));
    List<String> indexNamesWithoutRepository = filter(indexNames, indexName -> !indexName.contains(MigrationRepositoryIndex.INDEX_NAME));
    for (final String indexName : indexNamesWithoutRepository) {
      final List<Step> stepsForIndex = stepsRepository.findNotAppliedFor(indexName);
      final Plan plan = createPlanFor(indexName, srcVersion, dstVersion, stepsForIndex);
      migrateIndex(plan);
    }

    resetSettingsFromReindex(dstVersion, srcSettings);
    deleteSrcSchema(srcVersion);
  }

  protected void migrateIndex(final Plan plan) throws IOException, MigrationException {
    logger.info("Execute plan: {} ", plan);

    plan.executeOn(retryElasticsearchClient);

    for (final Step step : plan.getSteps()) {
        step.setApplied(true)
            .setAppliedDate(OffsetDateTime.now());
       stepsRepository.save(step);
    }
  }

  protected void deleteSrcSchema(final String srcVersion) throws IOException {
    if (migrationProperties.isDeleteSrcSchema()) {
        deleteSchema(srcVersion);
    }
  }

  private void resetSettingsFromReindex(String version, Map<String, String> previousSettings) {
    OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    String indexPattern = getIndexPatternFor(version);
    String numberOfReplicas = previousSettings.getOrDefault(NUMBERS_OF_REPLICA, "" + elsConfig.getNumberOfReplicas());
    String refreshInterval = previousSettings.getOrDefault(REFRESH_INTERVAL, elsConfig.getRefreshInterval());
    retryElasticsearchClient
        .setIndexSettingsFor(Settings.builder().put(NUMBERS_OF_REPLICA, numberOfReplicas).put(REFRESH_INTERVAL, refreshInterval).build(), indexPattern);
    retryElasticsearchClient.refresh(indexPattern);
  }

  private void setSettingsForReindex(String version) {
    String indexPattern = getIndexPatternFor(version);
    retryElasticsearchClient.setIndexSettingsFor(Settings.builder().put(NUMBERS_OF_REPLICA, "0").put(REFRESH_INTERVAL, "-1").build(), indexPattern);
  }

  private String getIndexNameFor(String indexMainName, String version) {
    return String.format("%s-%s-%s_", getIndexPrefix(), indexMainName, version);
  }

  private String getIndexPatternFor(String version) {
    // _ to match versions correct
    // first * to match index names
    // second * to match archive index names
    return String.format("%s-*-%s_*", getIndexPrefix(), version);
  }

  private void deleteSchema(final String version) throws IOException {
    deleteTemplates(version);
    deleteIndices(version);
  }

  protected void deleteTemplates(final String version) {
    final String templatesPattern = getIndexPrefix() + "-*-" + version + "_template";

    boolean templatesDeleted = retryElasticsearchClient.deleteTemplatesFor(templatesPattern);
    if (templatesDeleted) {
      logger.info("Templates with pattern {} deleted", templatesPattern);
    } else {
      logger.info("Templates with pattern {} NOT deleted", templatesPattern);
    }
  }

  protected void deleteIndices(final String version) {
    final String indexPattern = getIndexPatternFor(version);

    boolean indicesDeleted = retryElasticsearchClient.deleteIndicesFor(indexPattern);
    if (indicesDeleted) {
      logger.info("Indices with pattern {} deleted ", indexPattern);
    } else {
      logger.info("Indices with pattern {} NOT deleted ", indexPattern);
    }
  }

  protected Plan createPlanFor(final String indexName,final String srcVersion, final String dstVersion,final List<Step> steps) {
    final SemanticVersion sourceVersion = resolveOlderVersions(srcVersion);
    final SemanticVersion destinationVersion = resolveOlderVersions(dstVersion);

    final List<Step> sortByVersion = new ArrayList<>(steps);
    sortByVersion.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);

    final List<Step> onlyAffectedVersions = filter(sortByVersion, s -> SemanticVersion.fromVersion(s.getVersion()).isBetween(sourceVersion, destinationVersion));

    return new ReindexPlan(getIndexPrefix() + "-" + indexName+"-" + srcVersion, getIndexPrefix() + "-" + indexName+"-" + dstVersion, onlyAffectedVersions);
  }

  protected SemanticVersion resolveOlderVersions(final String newStyleVersion) {
    switch (newStyleVersion) {
      case "1.2.0": return SemanticVersion.fromVersion("0.22.0");
      case "1.2.1": return SemanticVersion.fromVersion("0.22.1");
    default: return SemanticVersion.fromVersion(newStyleVersion);
    }
  }

  protected String getIndexPrefix() {
    return operateProperties.getElasticsearch().getIndexPrefix();
  }
}

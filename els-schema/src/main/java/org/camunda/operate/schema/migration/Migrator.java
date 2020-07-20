/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import static org.camunda.operate.util.CollectionUtil.filter;
import static org.camunda.operate.util.CollectionUtil.map;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import org.camunda.operate.property.MigrationProperties;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.templates.TemplateDescriptor;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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

  @Autowired
  private List<IndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  @Autowired
  private OperateProperties operateProperties;
  
  @Autowired
  private RestHighLevelClient esClient;
  
  @Autowired
  private StepsRepository stepsRepository;

  @Autowired
  private MigrationProperties migrationProperties;

  private boolean shouldDeleteSrcSchema = true;
  
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
   * @param sourceVersion 
   * @param destinationVersion
   * @return whether migration was successful
   */
  public void migrate(final String sourceVersion,final String destinationVersion) {
    logger.info("Check whether migration is needed ...");
    final Optional<String> srcVersion = sourceVersion != null ? Optional.of(sourceVersion) : detectPreviousSchemaVersion();
    final Optional<String> dstVersion = destinationVersion != null ? Optional.of(destinationVersion) : Optional.of(operateProperties.getSchemaVersion());

    if (srcVersion.isPresent()) {
      logger.info("Detected previous Operate Elasticsearch schema: {}", srcVersion.get());
      try {
        migrateFromTo(srcVersion.get(), dstVersion.get());
      }catch(Exception e) {
        throw new OperateRuntimeException(String.format("Migration from %s to %s failed", srcVersion.get(), dstVersion.get()),e);
      }
    } else {
      logger.info("No previous Operate Elasticsearch schema found. No migration needed.");
    }
  }
  /**
   * Detects already existing operate schemas, but returns only one.
   * @return an optional String containing the detected schema version.
   */
  public Optional<String> detectPreviousSchemaVersion() {
    final List<String> indexNames = getAllIndexNames(getIndexPrefix() + "*");
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
        throw new OperateRuntimeException(String.format("Found %d Operate Schema versions: %s .Can only upgrade from one.", versions.size(), versions));
      }
      return Optional.of(versions.iterator().next());
    }
  }
  
  private List<String> getAllIndexNames(final String namePattern){
    try {
      final GetIndexResponse response = esClient.indices().get(new GetIndexRequest(namePattern), RequestOptions.DEFAULT);
      return List.of(response.getIndices());
    } catch (Exception e) {
      return List.of();
    }
  }
 
  private void migrateFromTo(final String srcVersion,final String dstVersion) {
    List<String> indexNames = map(indexDescriptors, IndexDescriptor::getMainIndexName);
    indexNames.addAll(map(templateDescriptors, TemplateDescriptor::getIndexNameFormat));
    for (final String indexName : indexNames) {
      final List<Step> stepsForIndex = stepsRepository.findNotAppliedFor(indexName);
      final Plan plan = createPlanFor(indexName,srcVersion,  dstVersion, stepsForIndex);
      migrateIndex(plan);
    }
    deleteSrcSchema(srcVersion);
  }

  protected void migrateIndex(final Plan plan) {
    logger.info("Execute plan: {} ", plan);
    
    plan.executeOn(esClient);
    
    for (final Step step : plan.getSteps()) {
        step.setApplied(true)
            .setAppliedDate(OffsetDateTime.now());
       stepsRepository.save(step);
    }
  }

  protected void deleteSrcSchema(final String srcVersion) {
    if (shouldDeleteSrcSchema) {
      deleteSchema(srcVersion);
    }
  }
  
  private void deleteSchema(final String version) {
    deleteTemplates(version); 
    deleteIndices(version);
  }

  protected void deleteTemplates(final String version) {
    final String templatesPattern = getIndexPrefix() + "-*-" + version + "_template";
    
    if (!templatesExist(templatesPattern)) {
      return;
    }
    
    try {
      boolean templatesDeleted = esClient.indices()
            .deleteTemplate(new DeleteIndexTemplateRequest(templatesPattern), RequestOptions.DEFAULT)
            .isAcknowledged();
      if (templatesDeleted) {
        logger.info("Templates with pattern {} deleted", templatesPattern);
      } else {
        logger.info("Templates with pattern {} NOT deleted", templatesPattern);
      }
    } catch (Exception e) {
      logger.error("Deleting templates with pattern {} failed", getIndexPrefix(),e);
    }
  }

  protected void deleteIndices(final String version) {
    final String indexPattern = getIndexPrefix() + "-*-" + version + "_*";
    
    if (!indicesExist(indexPattern)) {
      return;
    }
    
    try {
      boolean indicesDeleted = esClient.indices()
          .delete(new DeleteIndexRequest(indexPattern), RequestOptions.DEFAULT)
          .isAcknowledged();
      if (indicesDeleted) {
        logger.info("Indices with pattern {} deleted ", indexPattern);
      } else {
        logger.info("Indices with pattern {} NOT deleted ", indexPattern);
      }
    } catch (Exception e) {
      logger.error("Deleting indices with pattern {} failed", indexPattern, e);
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
  
  protected boolean templatesExist(final String templatePattern) {
    try {
      return esClient.indices().existsTemplate(new IndexTemplatesExistRequest(templatePattern), RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Failed to check existence of templates with pattern {}, continue assuming they don't exist", templatePattern, e);
      return false;
    }
  }
  
  protected boolean indicesExist(final String indexPattern) {
    try {
      return esClient.indices().exists(new GetIndexRequest(indexPattern), RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Failed to check existence of indices with pattern {}, continue assuming they don't exist",indexPattern, e);
      return false;
    }
  }

}

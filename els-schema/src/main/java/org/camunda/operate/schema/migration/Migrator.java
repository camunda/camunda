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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
/**
 * Migrates an operate schema from one version to another.
 * Requires an already created destination schema  provided by a schema manager.
 * 
 * Tries to detect source/previous schema if not provided.
 *
 */
@Profile("migration")
@DependsOn("schemaManager")
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

  private boolean shouldDeleteSrcSchema = true;
  
  @PostConstruct
  private void init() {
    logger.debug("Created Migrator for elasticsearch at {}:{} ",operateProperties.getElasticsearch().getHost(),operateProperties.getElasticsearch().getPort());
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
  public boolean migrate(final String sourceVersion,final String destinationVersion) {
    final Optional<String> srcVersion = sourceVersion != null ? Optional.of(sourceVersion) : detectPreviousSchemaVersion();
    final Optional<String> dstVersion = destinationVersion != null ? Optional.of(destinationVersion) : Optional.of(operateProperties.getSchemaVersion());
    
    if (srcVersion.isPresent()) {
      logger.info("Detected previous Operate Elasticsearch schema: {}", srcVersion.get());
      try {
        migrateFromTo(srcVersion.get(), dstVersion.get());
      }catch(Exception e) {
        logger.error("Migration from {} to {} failed", srcVersion.get(), dstVersion.get(), e);
        return false;
      }
      return true;
    } else {
      logger.info("No previous Operate Elasticsearch schema found.");
      return false;
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
      if (deleteSchema(srcVersion)) {
        logger.info("Templates and indices for version {} are deleted", srcVersion);
      } else {
        logger.info("Templates and indices for version {} are NOT deleted", srcVersion);
      }
    }
  }
  
  private boolean deleteSchema(final String version) {
    return deleteTemplates(version) && deleteIndices(version);
  }

  protected boolean deleteTemplates(final String version) {
    try {
      return esClient.indices()
          .deleteTemplate(new DeleteIndexTemplateRequest(getIndexPrefix() + "-*-" + version + "_template"), RequestOptions.DEFAULT)
          .isAcknowledged();
    } catch (IOException e) {
      logger.error("Deleting {} templates for {} failed", getIndexPrefix(), version, e);
    }
    return false;
  }

  protected boolean deleteIndices(final String version) {
    try {
      return esClient.indices()
          .delete(new DeleteIndexRequest(getIndexPrefix() + "-*-" + version + "_*"), RequestOptions.DEFAULT)
          .isAcknowledged();
    } catch (IOException e) {
      logger.error("Deleting {} indices for {} failed", getIndexPrefix(), version, e);
    }
    return false;
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

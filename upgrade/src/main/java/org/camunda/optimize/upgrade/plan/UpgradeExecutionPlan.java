/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UpgradeExecutionPlan implements UpgradePlan {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private OptimizeElasticsearchClient prefixAwareClient;

  private final List<UpgradeStep> upgradeSteps = new ArrayList<>();
  private ElasticsearchMetadataService metadataService;
  private ElasticSearchSchemaManager schemaManager;
  private String toVersion;
  private String fromVersion;
  private ESIndexAdjuster esIndexAdjuster;

  /**
   * Package only constructor prevents from building this upgrade execution plan manually.
   * Use {@link org.camunda.optimize.upgrade.plan.UpgradePlanBuilder} instead.
   */
  UpgradeExecutionPlan() {
  }

  public void addUpgradeDependencies(UpgradeExecutionDependencies upgradeDependencies) {
    final ConfigurationService configurationService = upgradeDependencies.getConfigurationService();
    final ObjectMapper objectMapper = upgradeDependencies.getObjectMapper();
    final OptimizeIndexNameService indexNameService = upgradeDependencies.getIndexNameService();

    metadataService = upgradeDependencies.getMetadataService();
    prefixAwareClient = upgradeDependencies.getPrefixAwareClient();
    schemaManager = new ElasticSearchSchemaManager(
      metadataService,
      configurationService,
      indexNameService,
      getMappings(),
      objectMapper
    );
    esIndexAdjuster = new ESIndexAdjuster(
      prefixAwareClient.getHighLevelClient(), indexNameService, configurationService
    );
  }

  @Override
  public void execute() {
    int currentStepCount = 1;
    for (UpgradeStep step : upgradeSteps) {
      logger.info(
        "Starting step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );

      try {
        step.execute(esIndexAdjuster);
      } catch (UpgradeRuntimeException e) {
        logger.error("The upgrade will be aborted. Please restore your Elasticsearch backup and try again.");
        throw e;
      }

      logger.info(
        "Successfully finished step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );
      currentStepCount++;
    }

    schemaManager.initializeSchema(prefixAwareClient);

    updateOptimizeVersion();
  }

  public List<IndexMappingCreator> getMappings() {
    final ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(StrictIndexMappingCreator.class));
    final Set<BeanDefinition> indexMapping =
      provider.findCandidateComponents(MetadataIndex.class.getPackage().getName());

    return indexMapping.stream()
      .map(beanDefinition -> {
        try {
          final Class<?> indexClass = Class.forName(beanDefinition.getBeanClassName());
          final Optional<Constructor<?>> noArgumentsConstructor = Arrays.stream(indexClass.getConstructors())
            .filter(constructor -> constructor.getParameterCount() == 0)
            .findFirst();

          return noArgumentsConstructor.map(constructor -> {
            try {
              return (IndexMappingCreator) constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
              throw new OptimizeRuntimeException("Failed initializing: " + beanDefinition.getBeanClassName(), e);
            }
          });
        } catch (ClassNotFoundException e) {
          throw new OptimizeRuntimeException("Failed initializing: " + beanDefinition.getBeanClassName(), e);
        }
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    this.upgradeSteps.add(upgradeStep);
  }

  public void setEsIndexAdjuster(final ESIndexAdjuster esIndexAdjuster) {
    this.esIndexAdjuster = esIndexAdjuster;
  }

  public void setSchemaManager(final ElasticSearchSchemaManager schemaManager) {
    this.schemaManager = schemaManager;
  }

  public void setMetadataService(final ElasticsearchMetadataService metadataService) {
    this.metadataService = metadataService;
  }

  public void setFromVersion(String fromVersion) {
    this.fromVersion = fromVersion;
  }

  public void setToVersion(String toVersion) {
    this.toVersion = toVersion;
  }

  private void updateOptimizeVersion() {
    logger.info("Updating Optimize Elasticsearch data structure version tag from {} to {}.", fromVersion, toVersion);
    metadataService.writeMetadata(prefixAwareClient, new MetadataDto(toVersion));
  }

}

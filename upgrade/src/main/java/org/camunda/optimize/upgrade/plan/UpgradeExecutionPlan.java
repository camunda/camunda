/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UpgradeExecutionPlan implements UpgradePlan {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private final RestHighLevelClient client;
  private final List<UpgradeStep> upgradeSteps = new ArrayList<>();

  private ValidationService validationService;
  private ElasticsearchMetadataService metadataService;
  private ElasticSearchSchemaManager schemaManager;
  private String toVersion;
  private String fromVersion;
  private ESIndexAdjuster esIndexAdjuster;

  public UpgradeExecutionPlan() {
    final ConfigurationService configurationService = new ConfigurationService();
    final ObjectMapper objectMapper = new ObjectMapper();
    metadataService = new ElasticsearchMetadataService(objectMapper);

    validationService = new ValidationService(configurationService, metadataService);
    validationService.validateConfiguration();

    client = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

    schemaManager = new ElasticSearchSchemaManager(
      metadataService,
      configurationService,
      getMappings(),
      objectMapper
    );
    esIndexAdjuster = new ESIndexAdjuster(client, configurationService);
  }

  @Override
  public void execute() {
    validationService.validateVersions(client, fromVersion, toVersion);

    int currentStepCount = 1;
    for (UpgradeStep step : upgradeSteps) {
      logger.info(
        "Starting step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );
      step.execute(esIndexAdjuster);
      logger.info(
        "Successfully finished step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );
      currentStepCount++;
    }

    schemaManager.initializeSchema(client);

    updateOptimizeVersion();
  }

  public List<TypeMappingCreator> getMappings() {
    final ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(StrictTypeMappingCreator.class));
    final Set<BeanDefinition> typeMapping = provider.findCandidateComponents(MetadataType.class.getPackage().getName());

    final List<TypeMappingCreator> mappingTypes = typeMapping.stream()
      .map(beanDefinition -> {
        try {
          return (TypeMappingCreator) Class.forName(beanDefinition.getBeanClassName()).getConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException e) {
          throw new OptimizeRuntimeException("Failed initializing: " + beanDefinition.getBeanClassName(), e);
        }
      })
      .collect(Collectors.toList());

    return mappingTypes;
  }

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    this.upgradeSteps.add(upgradeStep);
  }

  private void updateOptimizeVersion() {
    logger.info("Updating Optimize Elasticsearch data structure version tag from {} to {}.", fromVersion, toVersion);
    metadataService.writeMetadata(client, new MetadataDto(toVersion));
  }

  public void setEsIndexAdjuster(final ESIndexAdjuster esIndexAdjuster) {
    this.esIndexAdjuster = esIndexAdjuster;
  }

  public void setSchemaManager(final ElasticSearchSchemaManager schemaManager) {
    this.schemaManager = schemaManager;
  }

  public void setValidationService(final ValidationService validationService) {
    this.validationService = validationService;
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

}

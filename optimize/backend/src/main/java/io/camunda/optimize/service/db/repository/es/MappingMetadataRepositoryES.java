/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import io.camunda.optimize.service.db.es.MappingMetadataUtilES;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class MappingMetadataRepositoryES implements MappingMetadataRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(MappingMetadataRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;

  public MappingMetadataRepositoryES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public String[] getIndexAliasesWithImportIndexFlag(final boolean isImportIndex) {
    final MappingMetadataUtilES mappingUtil = new MappingMetadataUtilES(esClient);
    return mappingUtil.getAllMappings(esClient.getIndexNameService().getIndexPrefix()).stream()
        .filter(mapping -> isImportIndex == mapping.isImportIndex())
        .map(esClient.getIndexNameService()::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }
}

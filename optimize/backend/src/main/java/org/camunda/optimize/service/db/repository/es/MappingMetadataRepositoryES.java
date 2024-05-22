/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.repository.MappingMetadataRepository;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.MappingMetadataUtil;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class MappingMetadataRepositoryES implements MappingMetadataRepository {
  private final OptimizeElasticsearchClient esClient;
  private final OptimizeIndexNameService indexNameService;

  @Override
  public List<IndexMappingCreator<?>> getAllMappings() {
    MappingMetadataUtil mappingUtil = new MappingMetadataUtil(esClient);
    return mappingUtil.getAllMappings(indexNameService.getIndexPrefix());
  }
}

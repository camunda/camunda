/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class MappingMetadataRepositoryOS implements MappingMetadataRepository {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(MappingMetadataRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public MappingMetadataRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public List<IndexMappingCreator<?>> getAllMappings() {
    final MappingMetadataUtil mappingUtil = new MappingMetadataUtil(osClient);
    return mappingUtil.getAllMappings(indexNameService.getIndexPrefix());
  }
}

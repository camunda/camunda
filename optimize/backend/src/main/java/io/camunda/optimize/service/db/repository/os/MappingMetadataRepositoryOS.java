/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import io.camunda.optimize.service.db.os.MappingMetadataUtilOS;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class MappingMetadataRepositoryOS implements MappingMetadataRepository {
  private final OptimizeOpenSearchClient osClient;

  @Override
  public String[] getIndexAliasesWithImportIndexFlag(boolean isImportIndex) {
    MappingMetadataUtilOS mappingUtil = new MappingMetadataUtilOS(osClient);
    return mappingUtil.getAllMappings(osClient.getIndexNameService().getIndexPrefix()).stream()
        .filter(mapping -> isImportIndex == mapping.isImportIndex())
        .map(osClient.getIndexNameService()::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }
}

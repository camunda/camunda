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
import io.camunda.optimize.service.db.schema.BackupPriority;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class MappingMetadataRepositoryES implements MappingMetadataRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(MappingMetadataRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final MappingMetadataUtilES mappingUtil;

  public MappingMetadataRepositoryES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
    mappingUtil = new MappingMetadataUtilES(esClient);
  }

  @Override
  public String[] getIndexAliasesWithBackupPriority(final BackupPriority backupPriority) {
    return mappingUtil.getAllMappings(esClient.getIndexNameService().getIndexPrefix()).stream()
        .filter(mapping -> backupPriority == mapping.getBackupPriority())
        .map(esClient.getIndexNameService()::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }

  @Override
  public Set<String> getProcessDefinitionKeysWithInstanceIndex() {
    // Already lowercase, since these are index-name suffixes — normalized anyway so the interface's
    // promise holds even if the naming rules change.
    return mappingUtil
        .retrieveProcessInstanceIndexIdentifiers(esClient.getIndexNameService().getIndexPrefix())
        .stream()
        .map(key -> key.toLowerCase(Locale.ENGLISH))
        .collect(Collectors.toUnmodifiableSet());
  }
}

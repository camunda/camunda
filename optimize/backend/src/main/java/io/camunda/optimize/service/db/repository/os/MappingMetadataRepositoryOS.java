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
import io.camunda.optimize.service.db.schema.BackupPriority;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class MappingMetadataRepositoryOS implements MappingMetadataRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(MappingMetadataRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final MappingMetadataUtilOS mappingUtil;

  public MappingMetadataRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
    mappingUtil = new MappingMetadataUtilOS(osClient);
  }

  @Override
  public String[] getIndexAliasesWithBackupPriority(final BackupPriority backupPriority) {
    return mappingUtil.getAllMappings(osClient.getIndexNameService().getIndexPrefix()).stream()
        .filter(mapping -> backupPriority == mapping.getBackupPriority())
        .map(osClient.getIndexNameService()::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }

  @Override
  public Set<String> getProcessDefinitionKeysWithInstanceIndex() {
    // Already lowercase, since these are index-name suffixes — normalized anyway so the interface's
    // promise holds even if the naming rules change.
    return mappingUtil
        .retrieveProcessInstanceIndexIdentifiers(osClient.getIndexNameService().getIndexPrefix())
        .stream()
        .map(key -> key.toLowerCase(Locale.ENGLISH))
        .collect(Collectors.toUnmodifiableSet());
  }
}

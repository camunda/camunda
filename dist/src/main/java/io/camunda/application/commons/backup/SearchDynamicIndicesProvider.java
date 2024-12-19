/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.optimize.service.db.es.MappingMetadataUtilES;
import io.camunda.optimize.service.db.os.MappingMetadataUtilOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.webapps.backup.DynamicIndicesProvider;
import java.util.List;
import java.util.stream.Collectors;

public class SearchDynamicIndicesProvider implements DynamicIndicesProvider {

  private final MappingMetadataUtil<?> mappingMetadata;
  private final String indexPrefix;

  public SearchDynamicIndicesProvider(
      final DocumentBasedSearchClient searchClient,
      final boolean isElasticsearch,
      final String indexPrefix) {
    this.indexPrefix = indexPrefix;
    if (isElasticsearch) {
      mappingMetadata = new MappingMetadataUtilES(searchClient);
    } else {
      mappingMetadata = new MappingMetadataUtilOS(searchClient);
    }
  }

  @Override
  public List<String> getAllDynamicIndices() {
    return mappingMetadata.getAllDynamicMappings(indexPrefix).stream()
        .map(IndexMappingCreator::getIndexName)
        .collect(Collectors.toList());
  }
}

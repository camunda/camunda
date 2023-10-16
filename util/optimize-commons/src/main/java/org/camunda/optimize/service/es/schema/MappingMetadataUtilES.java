/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.db.schema.MappingMetadataUtil;
import org.camunda.optimize.service.db.schema.index.RichClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;

public class MappingMetadataUtilES extends MappingMetadataUtil {

  public MappingMetadataUtilES(final RichClient dbClient) {
    super(dbClient);
  }

  @Override
  protected Collection<? extends IndexMappingCreator<?>> getAllNonDynamicMappings() {
    return ElasticSearchSchemaManager.getAllNonDynamicMappings();
  }

  @Override
  public List<String> retrieveProcessInstanceIndexIdentifiers(final boolean eventBased) {
    final GetAliasesResponse aliases;
    final String prefix = eventBased ? EVENT_PROCESS_INSTANCE_INDEX_PREFIX : PROCESS_INSTANCE_INDEX_PREFIX;
    try {
      GetAliasesRequest request = new GetAliasesRequest();
      request.indices(prefix + "*");
      aliases = dbClient.getAlias(request);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + prefix, e);
    }
    return aliases.getAliases()
      .values()
      .stream()
      // Response requires filtering because it might include both event and non event based process instance indices
      // due to the shared alias
      .filter(aliasMetadataPerIndex -> filterProcessInstanceIndexAliases(aliasMetadataPerIndex, eventBased))
      .flatMap(aliasMetadataPerIndex -> aliasMetadataPerIndex.stream().map(AliasMetadata::alias))
      .filter(fullAliasName -> fullAliasName.contains(prefix))
      .map(fullIndexName -> fullIndexName.substring(fullIndexName.lastIndexOf(prefix) + prefix.length()))
      .toList();
  }

  @Override
  protected List<String> retrieveAllDynamicIndexKeysForPrefix(final String dynamicIndexPrefix) {
    final GetAliasesResponse aliases;
    try {
      aliases = dbClient.getAlias(new GetAliasesRequest(dynamicIndexPrefix + "*"));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix, e);
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(fullAliasName ->
             fullAliasName.substring(fullAliasName.lastIndexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
      .toList();
  }

  private boolean filterProcessInstanceIndexAliases(final Set<AliasMetadata> aliasMetadataSet,
                                                           final boolean eventBased) {
    if (eventBased) {
      return aliasMetadataSet.stream()
        .anyMatch(aliasMetadata -> aliasMetadata.getAlias().contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX));
    } else {
      return aliasMetadataSet.stream()
        .noneMatch(aliasMetadata -> aliasMetadata.getAlias().contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX));
    }
  }

}

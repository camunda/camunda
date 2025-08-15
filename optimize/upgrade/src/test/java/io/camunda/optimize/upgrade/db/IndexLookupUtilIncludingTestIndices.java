/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db;

import io.camunda.optimize.service.db.schema.DynamicIndexable;
import io.camunda.optimize.service.db.schema.IndexLookupUtil;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.es.indices.VariableUpdateInstanceIndexOldES;
import io.camunda.optimize.upgrade.os.indices.VariableUpdateInstanceIndexOldOS;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class IndexLookupUtilIncludingTestIndices {

  private static final Map<String, Function<String, IndexMappingCreator>> OS_INDEX_LOOKUP_MAP =
      createOpensearchIndexFunctionLookupMap();
  private static final Map<String, Function<String, IndexMappingCreator>> ES_INDEX_LOOKUP_MAP =
      createElasticsearchIndexFunctionLookupMap();

  private IndexLookupUtilIncludingTestIndices() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static IndexMappingCreator convertIndexForDatabase(
      final IndexMappingCreator indexToConvert, final DatabaseType databaseType) {
    if (databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      if (OS_INDEX_LOOKUP_MAP.containsKey(indexToConvert.getClass().getSimpleName())) {
        // If the key exists in the OS lookup map, it does not need converting in this type
        return indexToConvert;
      } else {
        // otherwise we get the index from the ES lookup
        return Optional.ofNullable(
                ES_INDEX_LOOKUP_MAP.get(indexToConvert.getClass().getSimpleName()))
            .map(indexName -> indexName.apply(getFunctionParameter(indexToConvert)))
            .orElse(IndexLookupUtil.convertIndexForDatabase(indexToConvert, databaseType));
      }
    } else if (databaseType.equals(DatabaseType.OPENSEARCH)) {
      if (ES_INDEX_LOOKUP_MAP.containsKey(indexToConvert.getClass().getSimpleName())) {
        // If the key exists in the ES lookup map, it does not need converting in this type
        return indexToConvert;
      } else {
        // otherwise we get the index from the OS lookup
        return Optional.ofNullable(
                OS_INDEX_LOOKUP_MAP.get(indexToConvert.getClass().getSimpleName()))
            .map(indexName -> indexName.apply(getFunctionParameter(indexToConvert)))
            .orElse(IndexLookupUtil.convertIndexForDatabase(indexToConvert, databaseType));
      }
    }
    throw new OptimizeRuntimeException("Cannot perform index lookup without a valid type");
  }

  private static Map<String, Function<String, IndexMappingCreator>>
      createOpensearchIndexFunctionLookupMap() {
    final Map<String, Function<String, IndexMappingCreator>> lookupMap = new HashMap<>();
    lookupMap.put(
        VariableUpdateInstanceIndexOldES.class.getSimpleName(),
        index -> new VariableUpdateInstanceIndexOldOS());
    return lookupMap;
  }

  private static Map<String, Function<String, IndexMappingCreator>>
      createElasticsearchIndexFunctionLookupMap() {
    final Map<String, Function<String, IndexMappingCreator>> lookupMap = new HashMap<>();
    lookupMap.put(
        VariableUpdateInstanceIndexOldOS.class.getSimpleName(),
        index -> new VariableUpdateInstanceIndexOldES());
    return lookupMap;
  }

  private static String getFunctionParameter(final IndexMappingCreator index) {
    if (index instanceof final DynamicIndexable dynamicIndex) {
      return dynamicIndex.getKey();
    }
    return null;
  }
}

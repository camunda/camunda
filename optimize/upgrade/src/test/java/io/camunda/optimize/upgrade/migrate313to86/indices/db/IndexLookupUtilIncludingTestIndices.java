/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import io.camunda.optimize.service.db.schema.DynamicIndexable;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.db.index.IndexLookupUtil;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.OnboardingStateIndexV2ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.ProcessInstanceArchiveIndexV8ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.es.SettingsIndexV2ES;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.OnboardingStateIndexV2OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.ProcessInstanceArchiveIndexV8OS;
import io.camunda.optimize.upgrade.migrate313to86.indices.os.SettingsIndexV2OS;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IndexLookupUtilIncludingTestIndices {

  private static final Map<String, Function<String, IndexMappingCreator>> osIndexLookupMap =
      createOpensearchIndexFunctionLookupMap();
  private static final Map<String, Function<String, IndexMappingCreator>> esIndexLookupMap =
      createElasticsearchIndexFunctionLookupMap();

  public static IndexMappingCreator convertIndexForDatabase(
      final IndexMappingCreator indexToConvert, final DatabaseType databaseType) {
    if (databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      if (osIndexLookupMap.containsKey(indexToConvert.getClass().getSimpleName())) {
        // If the key exists in the OS lookup map, it does not need converting in this type
        return indexToConvert;
      } else {
        // otherwise we get the index from the ES lookup
        return Optional.ofNullable(esIndexLookupMap.get(indexToConvert.getClass().getSimpleName()))
            .map(indexName -> indexName.apply(getFunctionParameter(indexToConvert)))
            .orElse(IndexLookupUtil.convertIndexForDatabase(indexToConvert, databaseType));
      }
    } else if (databaseType.equals(DatabaseType.OPENSEARCH)) {
      if (esIndexLookupMap.containsKey(indexToConvert.getClass().getSimpleName())) {
        // If the key exists in the ES lookup map, it does not need converting in this type
        return indexToConvert;
      } else {
        // otherwise we get the index from the OS lookup
        return Optional.ofNullable(osIndexLookupMap.get(indexToConvert.getClass().getSimpleName()))
            .map(indexName -> indexName.apply(getFunctionParameter(indexToConvert)))
            .orElse(IndexLookupUtil.convertIndexForDatabase(indexToConvert, databaseType));
      }
    }
    throw new OptimizeRuntimeException("Cannot perform index lookup without a valid type");
  }

  private static Map<String, Function<String, IndexMappingCreator>>
      createOpensearchIndexFunctionLookupMap() {
    Map<String, Function<String, IndexMappingCreator>> lookupMap = new HashMap<>();
    lookupMap.put(
        OnboardingStateIndexV2ES.class.getSimpleName(), index -> new OnboardingStateIndexV2OS());
    lookupMap.put(SettingsIndexV2ES.class.getSimpleName(), index -> new SettingsIndexV2OS());
    return lookupMap;
  }

  private static Map<String, Function<String, IndexMappingCreator>>
      createElasticsearchIndexFunctionLookupMap() {
    Map<String, Function<String, IndexMappingCreator>> lookupMap = new HashMap<>();
    lookupMap.put(
        OnboardingStateIndexV2OS.class.getSimpleName(), index -> new OnboardingStateIndexV2ES());
    lookupMap.put(SettingsIndexV2OS.class.getSimpleName(), index -> new SettingsIndexV2ES());
    lookupMap.put(
        ProcessInstanceArchiveIndexV8OS.class.getSimpleName(),
        ProcessInstanceArchiveIndexV8ES::new);
    return lookupMap;
  }

  private static String getFunctionParameter(final IndexMappingCreator index) {
    if (index instanceof DynamicIndexable dynamicIndex) {
      return dynamicIndex.getKey();
    }
    return null;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;

public abstract class MappingMetadataUtil<BUILDER> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MappingMetadataUtil.class);
  private final DatabaseClient dbClient;

  public MappingMetadataUtil(final DatabaseClient dbClient) {
    Objects.requireNonNull(dbClient, "dbClient cannot be null");
    this.dbClient = dbClient;
  }

  public List<IndexMappingCreator<BUILDER>> getAllMappings(final String indexPrefix) {
    final List<IndexMappingCreator<BUILDER>> allMappings = new ArrayList<>();
    allMappings.addAll(getAllNonDynamicMappings());
    allMappings.addAll(getAllDynamicMappings(indexPrefix));
    return allMappings;
  }

  public List<IndexMappingCreator<BUILDER>> getAllDynamicMappings(final String indexPrefix) {
    final List<IndexMappingCreator<BUILDER>> dynamicMappings = new ArrayList<>();
    dynamicMappings.addAll(retrieveAllProcessInstanceIndices(indexPrefix));
    dynamicMappings.addAll(retrieveAllDecisionInstanceIndices());
    return dynamicMappings;
  }

  protected abstract DecisionInstanceIndex<BUILDER> getDecisionInstanceIndex(final String key);

  protected abstract ProcessInstanceIndex<BUILDER> getProcessInstanceIndex(final String key);

  protected abstract Collection<? extends IndexMappingCreator<BUILDER>> getAllNonDynamicMappings();

  public List<String> retrieveProcessInstanceIndexIdentifiers(final String configuredIndexPrefix) {
    final Map<String, Set<String>> aliases;
    final String fullIndexPrefix = configuredIndexPrefix + "-" + PROCESS_INSTANCE_INDEX_PREFIX;
    try {
      aliases = dbClient.getAliasesForIndexPattern(fullIndexPrefix + "*");
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          "Failed retrieving aliases for dynamic index prefix " + fullIndexPrefix, e);
    }
    return aliases.entrySet().stream()
        .flatMap(aliasMetadataPerIndex -> aliasMetadataPerIndex.getValue().stream())
        .filter(fullAliasName -> fullAliasName.contains(fullIndexPrefix))
        .map(
            fullAliasName ->
                fullAliasName.substring(
                    fullAliasName.indexOf(fullIndexPrefix) + fullIndexPrefix.length()))
        .toList();
  }

  private List<DecisionInstanceIndex<BUILDER>> retrieveAllDecisionInstanceIndices() {
    return extractIndicesToClass(
        () -> retrieveAllDynamicIndexKeysForPrefix(DECISION_INSTANCE_INDEX_PREFIX),
        this::getDecisionInstanceIndex);
  }

  private List<ProcessInstanceIndex<BUILDER>> retrieveAllProcessInstanceIndices(
      final String indexPrefix) {
    return extractIndicesToClass(
        () -> retrieveProcessInstanceIndexIdentifiers(indexPrefix), this::getProcessInstanceIndex);
  }

  private List<String> retrieveAllDynamicIndexKeysForPrefix(final String dynamicIndexPrefix) {
    try {
      return dbClient.getAllIndicesForAlias(dynamicIndexPrefix + "*").stream()
          .map(
              fullAliasName ->
                  fullAliasName.substring(
                      fullAliasName.indexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
          .toList();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          "Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix, e);
    }
  }

  private <T> List<T> extractIndicesToClass(
      final Supplier<List<String>> defKeySupplier,
      final Function<String, T> convertKeyToIndexFunction) {
    return defKeySupplier.get().stream().map(convertKeyToIndexFunction).toList();
  }
}

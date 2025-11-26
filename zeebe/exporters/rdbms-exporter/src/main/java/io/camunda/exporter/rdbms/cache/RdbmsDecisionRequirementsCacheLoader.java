/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsDecisionRequirementsCacheLoader
    implements CacheLoader<Long, CachedDecisionRequirementsEntity> {

  private static final Logger LOG =
      LoggerFactory.getLogger(RdbmsDecisionRequirementsCacheLoader.class);
  private final DecisionRequirementsDbReader reader;

  public RdbmsDecisionRequirementsCacheLoader(final DecisionRequirementsDbReader reader) {
    this.reader = reader;
  }

  @Override
  public CachedDecisionRequirementsEntity load(final @NotNull Long decisionRequirementsKey)
      throws Exception {
    final var response = reader.findOne(decisionRequirementsKey);
    if (response.isPresent()) {
      final var dre = response.get();
      return new CachedDecisionRequirementsEntity(
          dre.decisionRequirementsKey(), dre.name(), dre.version());
    }
    LOG.debug("DecisionRequirements '{}' not found in RDBMS", decisionRequirementsKey);
    return null;
  }

  @Override
  public @NotNull Map<Long, CachedDecisionRequirementsEntity> loadAll(
      final @NotNull Set<? extends Long> decisionRequirementsKeys) {
    final var query =
        DecisionRequirementsQuery.of(
            b -> b.filter(f -> f.decisionRequirementsKeys(List.copyOf(decisionRequirementsKeys))));
    final var response = reader.search(query);
    return response.items().stream()
        .collect(
            Collectors.toMap(
                DecisionRequirementsEntity::decisionRequirementsKey,
                dre ->
                    new CachedDecisionRequirementsEntity(
                        dre.decisionRequirementsKey(), dre.name(), dre.version())));
  }
}

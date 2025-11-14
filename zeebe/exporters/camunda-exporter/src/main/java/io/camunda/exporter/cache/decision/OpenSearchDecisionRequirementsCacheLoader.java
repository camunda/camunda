/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.decision;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchDecisionRequirementsCacheLoader
    implements CacheLoader<Long, CachedDecisionRequirementsEntity> {
  private static final Logger LOG =
      LoggerFactory.getLogger(OpenSearchDecisionRequirementsCacheLoader.class);

  private final OpenSearchClient client;
  private final String decisionRequirementsIndexName;

  public OpenSearchDecisionRequirementsCacheLoader(
      final OpenSearchClient client, final String decisionRequirementsIndexName) {
    this.client = client;
    this.decisionRequirementsIndexName = decisionRequirementsIndexName;
  }

  @Override
  public CachedDecisionRequirementsEntity load(final Long decisionRequirementsKey)
      throws Exception {
    final var response =
        client.get(
            request ->
                request
                    .index(decisionRequirementsIndexName)
                    .id(String.valueOf(decisionRequirementsKey)),
            DecisionRequirementsEntity.class);
    if (response.found()) {
      final var decisionEntity = response.source();
      return new CachedDecisionRequirementsEntity(
          decisionEntity.getKey(), decisionEntity.getName(), decisionEntity.getVersion());
    } else {
      LOG.debug("DecisionRequirements '{}' not found in Opensearch", decisionRequirementsKey);
      return null;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.fact.FactType;
import java.util.List;
import java.util.Map;

/**
 * What the pipeline must pull from the fact stream for a cube, derived from a {@link
 * DatasetDeclaration} by the {@link DatasetCompiler}: which {@link FactType} feeds it, the WHERE
 * {@code filters} to apply before folding, and the {@code variableEnrichment} timing for each
 * declared variable dimension (so the projector stamps the right snapshot). Structural dimensions
 * need no enrichment and are absent from the map.
 */
public record FactBinding(
    FactType factType,
    List<FilterPredicate> filters,
    Map<String, EnrichmentTiming> variableEnrichment) {

  public FactBinding {
    filters = List.copyOf(filters);
    variableEnrichment = Map.copyOf(variableEnrichment);
  }
}

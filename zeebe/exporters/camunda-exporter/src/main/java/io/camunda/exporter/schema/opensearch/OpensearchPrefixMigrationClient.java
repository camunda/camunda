/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema.opensearch;

import io.camunda.exporter.schema.PrefixMigrationClient;
import io.camunda.exporter.utils.CloneResult;
import io.camunda.exporter.utils.ReindexResult;
import java.util.List;

public class OpensearchPrefixMigrationClient implements PrefixMigrationClient {

  @Override
  public ReindexResult reindex(final String source, final String destination) {
    return null;
  }

  @Override
  public CloneResult clone(final String source, final String destination) {
    return null;
  }

  @Override
  public List<String> getAllHistoricIndices(final String prefix) {
    return List.of();
  }
}

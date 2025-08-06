/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration.metric;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import java.io.IOException;

public interface UsageMetricMigrationClient {

  String LANG_PAINLESS = "painless";

  UsageMetricsEntity getFirstUsageMetricEntity(final String index, final SearchQuery searchQuery)
      throws IOException;

  ReindexResult reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script);

  UsageMetricsEntity getLatestMigratedEntity(String index, SearchQuery searchQuery)
      throws IOException;
}

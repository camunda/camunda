/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.WaitStateStatisticsEntity;
import io.camunda.search.query.WaitStateStatisticsQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

// STUB: returns an empty result. The data-layer task implements the terms aggregation
// (group by elementId on the wait_state index). See issue #56254 / parent #56239.
public class WaitStateStatisticsDocumentReader extends DocumentBasedReader
    implements WaitStateStatisticsReader {

  public WaitStateStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public List<WaitStateStatisticsEntity> aggregate(
      final WaitStateStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return List.of();
  }
}

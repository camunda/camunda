/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.ProcessInstanceFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class ProcessInstanceStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessInstanceStatisticsReader {

  public ProcessInstanceStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessInstanceFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .aggregate(
            query, ProcessInstanceFlowNodeStatisticsAggregationResult.class, resourceAccessChecks)
        .items();
  }
}

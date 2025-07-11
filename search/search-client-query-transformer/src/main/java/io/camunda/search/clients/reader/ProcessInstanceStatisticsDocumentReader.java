/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.ProcessInstanceFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessInstanceStatisticsQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class ProcessInstanceStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessInstanceStatisticsReader {

  public ProcessInstanceStatisticsDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessInstanceStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .aggregate(
            query, ProcessInstanceFlowNodeStatisticsAggregationResult.class, resourceAccessChecks)
        .items();
  }
}

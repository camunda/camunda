/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.transform;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.elasticsearch.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.transform.DataAggregator;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchDataAggregator extends DataAggregator {

  @Autowired private OperationReader operationReader;

  @Override
  public Map<String, BatchOperationDto> requestAndAddMetadata(
      final Map<String, BatchOperationDto> resultDtos, final List<String> idList) {

    final var metadataAggregations = createMetadataAggregations();
    final var batchIdAggregation =
        operationReader.getOperationsAggregatedByBatchOperationId(idList, metadataAggregations);

    for (final var bucket : batchIdAggregation.buckets().array()) {
      final var aggregations = bucket.aggregations().get(OperationTemplate.METADATA_AGGREGATION);
      if (aggregations == null || !aggregations.isFilters()) {
        continue;
      }
      final var filters = aggregations.filters();
      final int failedCount =
          (int)
              filters
                  .buckets()
                  .keyed()
                  .get(BatchOperationTemplate.FAILED_OPERATIONS_COUNT)
                  .docCount();
      final int completedCount =
          (int)
              filters
                  .buckets()
                  .keyed()
                  .get(BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT)
                  .docCount();
      final String batchId = bucket.key().stringValue();

      resultDtos
          .get(batchId)
          .setFailedOperationsCount(failedCount)
          .setCompletedOperationsCount(completedCount);
    }
    return resultDtos;
  }

  private Map<String, Aggregation> createMetadataAggregations() {
    final var failedQuery =
        ElasticsearchUtil.termsQuery(OperationTemplate.STATE, OperationState.FAILED);
    final var completedQuery =
        ElasticsearchUtil.termsQuery(OperationTemplate.STATE, OperationState.COMPLETED);

    final var filterBuckets =
        Map.of(
            BatchOperationTemplate.FAILED_OPERATIONS_COUNT, failedQuery,
            BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT, completedQuery);

    return Map.of(
        OperationTemplate.METADATA_AGGREGATION,
        Aggregation.of(
            a ->
                a.filters(
                    f ->
                        f.filters(
                            co.elastic.clients.elasticsearch._types.aggregations.Buckets.of(
                                b -> b.keyed(filterBuckets))))));
  }
}

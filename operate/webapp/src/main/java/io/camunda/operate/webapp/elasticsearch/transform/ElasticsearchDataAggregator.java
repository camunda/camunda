/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.transform;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.webapp.elasticsearch.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.transform.DataAggregator;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.util.List;
import java.util.Map;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
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

    final AggregationBuilder metadataAggregation =
        AggregationBuilders.filters(
            OperationTemplate.METADATA_AGGREGATION,
            new FiltersAggregator.KeyedFilter(
                BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
                QueryBuilders.termQuery(OperationTemplate.STATE, OperationState.FAILED)),
            new FiltersAggregator.KeyedFilter(
                BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                QueryBuilders.termQuery(OperationTemplate.STATE, OperationState.COMPLETED)));

    final Terms batchIdAggregation =
        operationReader.getOperationsAggregatedByBatchOperationId(idList, metadataAggregation);
    for (final Terms.Bucket bucket : batchIdAggregation.getBuckets()) {
      final ParsedFilters aggregations =
          bucket.getAggregations().get(OperationTemplate.METADATA_AGGREGATION);
      final int failedCount =
          (int)
              aggregations
                  .getBucketByKey(BatchOperationTemplate.FAILED_OPERATIONS_COUNT)
                  .getDocCount();
      final int completedCount =
          (int)
              aggregations
                  .getBucketByKey(BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT)
                  .getDocCount();
      final String batchId = bucket.getKeyAsString();

      resultDtos
          .get(batchId)
          .setFailedOperationsCount(failedCount)
          .setCompletedOperationsCount(completedCount);
    }
    return resultDtos;
  }
}

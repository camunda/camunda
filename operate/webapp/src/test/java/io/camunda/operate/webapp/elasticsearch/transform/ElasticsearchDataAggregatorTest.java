/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.BatchOperationTestDataHelper;
import io.camunda.operate.webapp.elasticsearch.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchDataAggregatorTest {

  @Mock OperationReader operationReader;
  @Mock private ObjectMapper objectMapper;
  @InjectMocks private ElasticsearchDataAggregator underTest;

  private List<BatchOperationEntity> testEntities;

  @Test
  public void testEnrichBatchEntitiesWithMetadataSuccess() {

    testEntities = BatchOperationTestDataHelper.create2TestBatchOperationDtos();
    final List<BatchOperationDto> expectedDtos =
        BatchOperationTestDataHelper.get2DtoBatchRequestExpected();

    final StringTermsAggregate mockTermsAggregate = createMockStringTermsAggregate();
    when(operationReader.getOperationsAggregatedByBatchOperationId(anyList(), anyMap()))
        .thenReturn(mockTermsAggregate);

    final List<BatchOperationDto> actualBatchOperationDtos =
        underTest.enrichBatchEntitiesWithMetadata(testEntities);
    assertThat(actualBatchOperationDtos.size())
        .as("Two result entities expected but got " + actualBatchOperationDtos.size())
        .isEqualTo(2);

    final BatchOperationDto actual1 = actualBatchOperationDtos.get(0);
    final BatchOperationDto actual2 = actualBatchOperationDtos.get(1);

    assertThat(expectedDtos.get(0)).as("actual1 is not equal to expected DTO.").isEqualTo(actual1);
    assertThat(expectedDtos.get(1)).as("actual2 is not equal to expected DTO.").isEqualTo(actual2);
  }

  private StringTermsAggregate createMockStringTermsAggregate() {
    // Create two buckets for two batch operations
    final var bucket1 =
        StringTermsBucket.of(
            b ->
                b.key(testEntities.get(0).getId())
                    .docCount(5)
                    .aggregations(createFilterAggregation(1, 4)));
    final var bucket2 =
        StringTermsBucket.of(
            b ->
                b.key(testEntities.get(1).getId())
                    .docCount(3)
                    .aggregations(createFilterAggregation(3, 0)));

    return StringTermsAggregate.of(
        a -> a.buckets(b -> b.array(List.of(bucket1, bucket2))).sumOtherDocCount(0L));
  }

  private Map<String, Aggregate> createFilterAggregation(
      final long completedCount, final long failedCount) {
    final var completedBucket =
        FiltersBucket.of(b -> b.docCount(completedCount).aggregations(Map.of()));
    final var failedBucket = FiltersBucket.of(b -> b.docCount(failedCount).aggregations(Map.of()));

    final var filtersAgg =
        FiltersAggregate.of(
            f ->
                f.buckets(
                    b ->
                        b.keyed(
                            Map.of(
                                BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                                completedBucket,
                                BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
                                failedBucket))));

    return Map.of("metadataAggregation", Aggregate.of(a -> a.filters(filtersAgg)));
  }
}

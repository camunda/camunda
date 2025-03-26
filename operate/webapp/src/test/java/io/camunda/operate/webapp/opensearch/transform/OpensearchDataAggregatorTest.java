/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.BatchOperationTestDataHelper;
import io.camunda.operate.util.OpensearchMocks;
import io.camunda.operate.webapp.elasticsearch.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregate.Builder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.util.ObjectBuilder;

@ExtendWith(MockitoExtension.class)
public class OpensearchDataAggregatorTest {

  @Mock RichOpenSearchClient richOpenSearchClient;
  @Mock OpenSearchDocumentOperations openSearchDocumentOperations;
  @Mock private OperationTemplate operationTemplate;
  @Mock private BatchOperationTemplate batchOperationTemplate;
  @Mock private OperationReader operationReader;

  private List<BatchOperationEntity> testEntities;
  @InjectMocks private OpensearchDataAggregator underTest;

  @Test
  public void testEnrichBatchEntitiesWithMetadataSuccess() {
    testEntities = BatchOperationTestDataHelper.create2TestBatchOperationDtos();
    final List<BatchOperationDto> expectedDtos =
        BatchOperationTestDataHelper.get2DtoBatchRequestExpected();

    when(richOpenSearchClient.doc()).thenReturn(openSearchDocumentOperations);
    when(openSearchDocumentOperations.search(any(SearchRequest.Builder.class), any(Class.class)))
        .thenReturn(mockBasicResponse());

    final List<BatchOperationDto> actualBatchOperationDtos =
        underTest.enrichBatchEntitiesWithMetadata(testEntities);
    assertEquals(
        2,
        actualBatchOperationDtos.size(),
        "Two result entities expected but got " + actualBatchOperationDtos.size());

    assertEquals(
        actualBatchOperationDtos.get(0),
        expectedDtos.get(0),
        "actual1 is not equal to expected DTO.");
    assertEquals(
        actualBatchOperationDtos.get(1),
        expectedDtos.get(1),
        "actual2 is not equal to expected DTO.");
  }

  private SearchResponse<OperationEntity> mockBasicResponse() {
    final Map<String, Function<Builder, ObjectBuilder<Aggregate>>> subFilterAggregations =
        new HashMap<>();
    subFilterAggregations.put(
        testEntities.get(0).getId(),
        OpensearchMocks.mockTwoFilterAggregation(
            BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
            1,
            BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
            4));
    subFilterAggregations.put(
        testEntities.get(1).getId(),
        OpensearchMocks.mockTwoFilterAggregation(
            BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
            3,
            BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
            0));

    return OpensearchMocks.getMockResponseOf(
        // hits content doesn't matter, because entity info is taken from the initially passed
        // entity
        OpensearchMocks.mockTwoHits(null, null),
        OperationTemplate.BATCH_OPERATION_ID_AGGREGATION,
        OpensearchMocks.mockTermsAggregationWithSubaggregations(
            OperationTemplate.METADATA_AGGREGATION, subFilterAggregations, 8L));
  }
}

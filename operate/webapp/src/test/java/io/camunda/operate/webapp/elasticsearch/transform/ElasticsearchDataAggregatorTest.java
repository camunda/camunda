/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.BatchOperationTestDataHelper;
import io.camunda.operate.util.ElasticsearchMocks;
import io.camunda.operate.webapp.elasticsearch.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchDataAggregatorTest {

  @Mock OperationReader operationReader;
  @Mock private OperationTemplate operationTemplate;
  @Mock private ObjectMapper objectMapper;
  @InjectMocks private ElasticsearchDataAggregator underTest;

  private List<BatchOperationEntity> testEntities;

  @Test
  public void testEnrichBatchEntitiesWithMetadataSuccess()
      throws IOException, NoSuchFieldException, IllegalAccessException {

    testEntities = BatchOperationTestDataHelper.create2TestBatchOperationDtos();
    final List<BatchOperationDto> expectedDtos =
        BatchOperationTestDataHelper.get2DtoBatchRequestExpected();

    final Terms mockTerms =
        ElasticsearchMocks.getMockTermsFromJSONResponse(
            getTestMockJSONResponse(), OperationTemplate.BATCH_OPERATION_ID_AGGREGATION);
    when(operationReader.getOperationsAggregatedByBatchOperationId(
            anyList(), any(AggregationBuilder.class)))
        .thenReturn(mockTerms);

    final List<BatchOperationDto> actualBatchOperationDtos =
        underTest.enrichBatchEntitiesWithMetadata(testEntities);
    assertEquals(
        2,
        actualBatchOperationDtos.size(),
        "Two result entities expected but got " + actualBatchOperationDtos.size());

    final BatchOperationDto actual1 = actualBatchOperationDtos.get(0);
    final BatchOperationDto actual2 = actualBatchOperationDtos.get(1);

    assertEquals(actual1, expectedDtos.get(0), "actual1 is not equal to expected DTO.");
    assertEquals(actual2, expectedDtos.get(1), "actual2 is not equal to expected DTO.");
  }

  private String getTestMockJSONResponse() {
    final String mockESResponseHits =
        ElasticsearchMocks.hitsFromTemplate(
            new String[] {
              // hits content doesn't matter, because entity info is taken from the initially passed
              // entity
              ElasticsearchMocks.instanceFromTemplate(
                  "2",
                  "2",
                  OperationType.MODIFY_PROCESS_INSTANCE.name(),
                  testEntities.get(0).getId(),
                  BatchOperationTestDataHelper.TEST_USER),
              ElasticsearchMocks.instanceFromTemplate(
                  "20",
                  "20",
                  OperationType.MODIFY_PROCESS_INSTANCE.name(),
                  testEntities.get(1).getId(),
                  BatchOperationTestDataHelper.TEST_USER)
            });

    final String filterAggregation1 =
        ElasticsearchMocks.termsSubaggregationFilterFromTemplate(
            5,
            ElasticsearchMocks.twoBucketFilterAggregationFromTemplate(
                OperationTemplate.METADATA_AGGREGATION,
                BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                1,
                BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
                4),
            testEntities.get(0).getId());
    final String filterAggregation2 =
        ElasticsearchMocks.termsSubaggregationFilterFromTemplate(
            3,
            ElasticsearchMocks.twoBucketFilterAggregationFromTemplate(
                OperationTemplate.METADATA_AGGREGATION,
                BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                3,
                BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
                0),
            testEntities.get(1).getId());

    final String mockResponse =
        ElasticsearchMocks.batchSearchResponseWithAggregationAsString(
            8,
            mockESResponseHits,
            ElasticsearchMocks.termsAggregationFromTemplate(
                OperationTemplate.BATCH_OPERATION_ID_AGGREGATION,
                new String[] {filterAggregation1, filterAggregation2}));
    return mockResponse;
  }
}

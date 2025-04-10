/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper.createFrom;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.rest.BatchOperationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class BatchOperationRestServiceIT extends OperateSearchAbstractIT {
  public static final String[] TEST_BATCH_OP_IDS = {"1", "2", "3"};

  @Autowired MockMvcManager mockMvcManager;
  @Autowired BatchOperationTemplate batchOperationTemplate;
  @Autowired OperationTemplate operationTemplate;
  @Autowired private UserService userService;
  private String operationIndexName;
  private String batchOperationIndexName;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    operationIndexName = operationTemplate.getFullQualifiedName();
    batchOperationIndexName = batchOperationTemplate.getFullQualifiedName();
    createData();
    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void testBatchOperationsCount() throws Exception {
    Mockito.when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(DEFAULT_USER));
    final List<BatchOperationDto> actual =
        postBatchOperationsRequest(new BatchOperationRequestDto().setPageSize(10));

    assertThat(actual.size()).isEqualTo(3);
    // check everything is in the right order (ordered by end date, ascending)
    final BatchOperationDto actual1 = actual.get(0);
    final BatchOperationDto actual2 = actual.get(1);
    final BatchOperationDto actual3 = actual.get(2);
    assertThat(actual1.getId()).isEqualTo(TEST_BATCH_OP_IDS[2]);
    assertThat(actual2.getId()).isEqualTo(TEST_BATCH_OP_IDS[0]);
    assertThat(actual3.getId()).isEqualTo(TEST_BATCH_OP_IDS[1]);
    // check operation count values are correct
    assertThat(actual1.getCompletedOperationsCount()).isEqualTo(0);
    assertThat(actual1.getFailedOperationsCount()).isEqualTo(0);
    assertThat(actual2.getCompletedOperationsCount()).isEqualTo(3);
    assertThat(actual2.getFailedOperationsCount()).isEqualTo(2);
    assertThat(actual3.getCompletedOperationsCount()).isEqualTo(2);
    assertThat(actual3.getFailedOperationsCount()).isEqualTo(0);
  }

  @Test
  public void testGetBatchOperationWithNoPageSize() throws Exception {
    // when
    final MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto());
    // then
    assertErrorMessageContains(mvcResult, "pageSize parameter must be provided.");
  }

  @Test
  public void testGetBatchOperationWithTooManyParams() throws Exception {
    // when
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            new BatchOperationRequestDto(
                2,
                createFrom(new Object[] {123, 123}, objectMapper),
                createFrom(new Object[] {123, 123}, objectMapper)));
    // then
    assertErrorMessageContains(
        mvcResult,
        "Only one of parameters must be present in request: either searchAfter or searchBefore.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchAfter() throws Exception {
    // when
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            new BatchOperationRequestDto(2, createFrom(new Object[] {123}, objectMapper), null));
    // then
    assertErrorMessageContains(mvcResult, "searchAfter must be an array of two values.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchBefore() throws Exception {
    // when
    final MvcResult mvcResult =
        postRequestThatShouldFail(
            new BatchOperationRequestDto(2, null, createFrom(new Object[] {123}, objectMapper)));
    // then
    assertErrorMessageContains(mvcResult, "searchBefore must be an array of two values.");
  }

  protected MvcResult postRequestThatShouldFail(final Object query) throws Exception {
    return mockMvcManager.postRequest(
        BatchOperationRestService.BATCH_OPERATIONS_URL, query, HttpStatus.SC_BAD_REQUEST);
  }

  private List<BatchOperationDto> postBatchOperationsRequest(final BatchOperationRequestDto query)
      throws Exception {
    final MvcResult mvcResult =
        mockMvcManager.postRequest(
            BatchOperationRestService.BATCH_OPERATIONS_URL, query, HttpStatus.SC_OK);
    final String response = mvcResult.getResponse().getContentAsString();
    final BatchOperationDto[] dtos = objectMapper.readValue(response, BatchOperationDto[].class);
    return Arrays.stream(dtos).toList();
  }

  private void createData() throws Exception {
    objectMapper.registerModule(new JavaTimeModule());
    final BatchOperationEntity bo1 =
        new BatchOperationEntity()
            .setId(TEST_BATCH_OP_IDS[0])
            .setType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setUsername(DEFAULT_USER)
            .setInstancesCount(1)
            .setOperationsTotalCount(5)
            .setOperationsFinishedCount(3)
            .setStartDate(OffsetDateTime.now().minus(10, ChronoUnit.SECONDS))
            .setEndDate(OffsetDateTime.now().minus(1, ChronoUnit.SECONDS));
    final BatchOperationEntity bo2 =
        new BatchOperationEntity()
            .setId(TEST_BATCH_OP_IDS[1])
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setUsername(DEFAULT_USER)
            .setInstancesCount(1)
            .setOperationsTotalCount(2)
            .setOperationsFinishedCount(2)
            .setStartDate(OffsetDateTime.now().minus(5, ChronoUnit.SECONDS))
            .setEndDate(OffsetDateTime.now().minus(3, ChronoUnit.SECONDS));
    final BatchOperationEntity bo3 =
        new BatchOperationEntity()
            .setId(TEST_BATCH_OP_IDS[2])
            .setType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setUsername(DEFAULT_USER)
            .setInstancesCount(1)
            .setOperationsTotalCount(0)
            .setOperationsFinishedCount(0)
            .setStartDate(OffsetDateTime.now().minus(6, ChronoUnit.SECONDS));
    testSearchRepository.createOrUpdateDocumentFromObject(
        batchOperationIndexName, bo1.getId(), bo1);
    testSearchRepository.createOrUpdateDocumentFromObject(
        batchOperationIndexName, bo2.getId(), bo2);
    testSearchRepository.createOrUpdateDocumentFromObject(
        batchOperationIndexName, bo3.getId(), bo3);

    final OperationEntity bo1op1 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[0])
            .setState(OperationState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo1op1.getId(), bo1op1);
    final OperationEntity bo1op2 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[0])
            .setState(OperationState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo1op2.getId(), bo1op2);
    final OperationEntity bo1op3 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[0])
            .setState(OperationState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo1op3.getId(), bo1op3);
    final OperationEntity bo1op4 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[0])
            .setState(OperationState.FAILED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo1op4.getId(), bo1op4);
    final OperationEntity bo1op5 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[0])
            .setState(OperationState.FAILED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo1op5.getId(), bo1op5);
    final OperationEntity bo2op1 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[1])
            .setState(OperationState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo2op1.getId(), bo2op1);
    final OperationEntity bo2op2 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[1])
            .setState(OperationState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo2op2.getId(), bo2op2);
    // batch operation 3 has no completed operations (to check for possible NPE issues on the
    // aggregations)
    final OperationEntity bo3op1 =
        new OperationEntity()
            .setBatchOperationId(TEST_BATCH_OP_IDS[2])
            .setState(OperationState.SENT);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationIndexName, bo3op1.getId(), bo3op1);
  }
}

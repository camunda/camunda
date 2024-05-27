/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper.createFrom;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.BatchOperationRestService;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      BatchOperationRestService.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class
    })
public class BatchOperationRestServiceIT extends OperateAbstractIT {

  @MockBean private BatchOperationReader batchOperationReader;

  private ObjectMapper objectMapper = new ObjectMapper();

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

  protected MvcResult postRequestThatShouldFail(Object query) throws Exception {
    return postRequestThatShouldFail(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }
}

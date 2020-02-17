/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.es.reader.BatchOperationReader;
import org.camunda.operate.webapp.rest.BatchOperationRestService;
import org.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, BatchOperationRestService.class}
)
public class BatchOperationRestServiceTest extends OperateIntegrationTest {

  @MockBean
  private BatchOperationReader batchOperationReader;

  @Test
  public void testGetBatchOperationWithNoPageSize() throws Exception {
    //when
    final MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto());
    //then
    assertErrorMessageContains(mvcResult, "pageSize parameter must be provided.");
  }

  @Test
  public void testGetBatchOperationWithTooManyParams() throws Exception {
    //when
    final MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, new Object[]{123, 123}, new Object[]{123, 123}));
    //then
    assertErrorMessageContains(mvcResult, "Only one of parameters must be present in request: either searchAfter or searchBefore.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchAfter() throws Exception {
    //when
    MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, new Object[]{123}, null));
    //then
    assertErrorMessageContains(mvcResult, "searchAfter must be an array of two long values.");

    //when
    mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, new Object[]{"adg", 234}, null));
    //then
    assertErrorMessageContains(mvcResult, "searchAfter must be an array of two long values.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchBefore() throws Exception {
    //when
    MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, null, new Object[]{123}));
    //then
    assertErrorMessageContains(mvcResult, "searchBefore must be an array of two long values.");

    //when
    mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, null, new Object[]{123, "asf"}));
    //then
    assertErrorMessageContains(mvcResult, "searchBefore must be an array of two long values.");
  }

  protected MvcResult postRequestThatShouldFail(Object query) throws Exception {
    return postRequestThatShouldFail(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.es.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.BatchOperationRestService;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.OperateProfileService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import static io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper.createFrom;

@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, BatchOperationRestService.class, OperateProfileService.class}
)
public class BatchOperationRestServiceTest extends OperateIntegrationTest {

  @MockBean
  private BatchOperationReader batchOperationReader;

  private ObjectMapper objectMapper = new ObjectMapper();

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
    final MvcResult mvcResult = postRequestThatShouldFail(
        new BatchOperationRequestDto(2, createFrom(new Object[] { 123, 123 }, objectMapper),
            createFrom(new Object[] { 123, 123 }, objectMapper)));
    //then
    assertErrorMessageContains(mvcResult, "Only one of parameters must be present in request: either searchAfter or searchBefore.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchAfter() throws Exception {
    //when
    MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, createFrom(new Object[]{123}, objectMapper), null));
    //then
    assertErrorMessageContains(mvcResult, "searchAfter must be an array of two values.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchBefore() throws Exception {
    //when
    MvcResult mvcResult = postRequestThatShouldFail(new BatchOperationRequestDto(2, null, createFrom(new Object[]{123}, objectMapper)));
    //then
    assertErrorMessageContains(mvcResult, "searchBefore must be an array of two values.");
  }

  protected MvcResult postRequestThatShouldFail(Object query) throws Exception {
    return postRequestThatShouldFail(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }

}

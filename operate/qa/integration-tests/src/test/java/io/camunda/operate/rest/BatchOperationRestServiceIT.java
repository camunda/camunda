/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper.createFrom;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.OperateProfileService;
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
      OperateProfileService.class
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
    MvcResult mvcResult =
        postRequestThatShouldFail(
            new BatchOperationRequestDto(2, createFrom(new Object[] {123}, objectMapper), null));
    // then
    assertErrorMessageContains(mvcResult, "searchAfter must be an array of two values.");
  }

  @Test
  public void testGetBatchOperationWithWrongSearchBefore() throws Exception {
    // when
    MvcResult mvcResult =
        postRequestThatShouldFail(
            new BatchOperationRequestDto(2, null, createFrom(new Object[] {123}, objectMapper)));
    // then
    assertErrorMessageContains(mvcResult, "searchBefore must be an array of two values.");
  }

  protected MvcResult postRequestThatShouldFail(Object query) throws Exception {
    return postRequestThatShouldFail(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }
}

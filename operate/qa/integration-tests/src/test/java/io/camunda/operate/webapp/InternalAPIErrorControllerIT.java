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
package io.camunda.operate.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.exception.InternalAPIException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Utilizes an endpoint from OperationRestService to test the error handling functionality
// of the abstract InternalAPIErrorController class
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
@ActiveProfiles({"test"})
@AutoConfigureMockMvc
public class InternalAPIErrorControllerIT {
  private static final String EXCEPTION_MESSAGE = "profile exception message";
  @Autowired private MockMvc mockMvc;
  @MockBean private OperationReader operationReader;
  @MockBean private OperateProfileService mockProfileService;
  @Autowired private ObjectMapper objectMapper;
  private MockHttpServletRequestBuilder mockGetRequest;

  @Before
  public void setup() {
    mockGetRequest = get("/api/operations").queryParam("batchOperationId", "abc");
    when(mockProfileService.getMessageByProfileFor(any())).thenReturn(EXCEPTION_MESSAGE);
  }

  @Test
  public void shouldReturn500ForOperateRuntimeException() throws Exception {
    OperateRuntimeException exception = new OperateRuntimeException("runtime exception");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());

    Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isNull();
  }

  @Test
  public void shouldReturn404ForRuntimeNotFoundException() throws Exception {
    io.camunda.operate.store.NotFoundException exception =
        new io.camunda.operate.store.NotFoundException("not found exception");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());

    Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isNull();
  }

  @Test
  public void shouldReturn400ForInternalAPIException() throws Exception {
    InternalAPIException exception = new InternalAPIException("internal api exception") {};
    exception.setInstance("instanceId");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isEqualTo(exception.getInstance());
  }

  @Test
  public void shouldReturn404ForInternalNotFoundException() throws Exception {
    NotFoundException exception = new NotFoundException("not found exception");
    exception.setInstance("instanceId");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());

    Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isEqualTo(exception.getInstance());
  }

  @Test
  public void shouldReturn403ForNotAuthorizedException() throws Exception {
    NotAuthorizedException exception = new NotAuthorizedException("not authorized exception");
    exception.setInstance("instanceId");

    when(operationReader.getOperationsByBatchOperationId(any())).thenThrow(exception);

    MvcResult result = mockMvc.perform(mockGetRequest).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());

    Map<String, Object> resultBody =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(resultBody.get("status")).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(resultBody.get("message")).isEqualTo(EXCEPTION_MESSAGE);
    assertThat(resultBody.get("instance")).isEqualTo(exception.getInstance());
  }
}

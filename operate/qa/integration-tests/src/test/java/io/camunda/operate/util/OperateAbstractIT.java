/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.util;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.operate.zeebe.PartitionHolder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@WebAppConfiguration
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public abstract class OperateAbstractIT {

  public static final String DEFAULT_USER = "testuser";

  @Rule public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  protected MockMvc mockMvc;

  protected OffsetDateTime testStartTime;

  @MockBean protected UserService userService;

  @MockBean protected TenantService tenantService;

  @Before
  public void before() {
    testStartTime = OffsetDateTime.now();
    mockMvc = mockMvcTestRule.getMockMvc();
    when(userService.getCurrentUser())
        .thenReturn(
            new UserDto().setUserId(DEFAULT_USER).setPermissions(List.of(Permission.WRITE)));
    mockTenantResponse();
  }

  protected void mockTenantResponse() {
    doReturn(TenantService.AuthenticatedTenants.allTenants())
        .when(tenantService)
        .getAuthenticatedTenants();
  }

  protected MvcResult getRequest(String requestUrl) throws Exception {
    return getRequest(requestUrl, mockMvcTestRule.getContentType());
  }

  protected MvcResult getRequest(String requestUrl, MediaType responseMediaType) throws Exception {
    final MockHttpServletRequestBuilder request = get(requestUrl).accept(responseMediaType);
    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(responseMediaType))
            .andReturn();

    return mvcResult;
  }

  protected MvcResult getRequestShouldFailWithException(
      String requestUrl, Class<? extends Exception> exceptionClass) throws Exception {
    final MockHttpServletRequestBuilder request =
        get(requestUrl).accept(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().is4xxClientError())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
        .andReturn();
  }

  protected MvcResult postRequestShouldFailWithException(
      String requestUrl, Class<? extends Exception> exceptionClass) throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl)
            .content("{}")
            .contentType(mockMvcTestRule.getContentType())
            .accept(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().is4xxClientError())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
        .andReturn();
  }

  protected MvcResult postRequest(String requestUrl, Object query) throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl)
            .content(mockMvcTestRule.json(query))
            .contentType(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(mockMvcTestRule.getContentType()))
        .andReturn();
  }

  protected MvcResult postRequestThatShouldFail(String requestUrl, Object query) throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl)
            .content(mockMvcTestRule.json(query))
            .contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn();
  }

  protected MvcResult postRequestThatShouldFail(String requestUrl, String stringContent)
      throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl).content(stringContent).contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn();
  }

  protected MvcResult getRequestShouldFailWithNoAuthorization(String requestUrl) throws Exception {
    final MockHttpServletRequestBuilder request =
        get(requestUrl).accept(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().isForbidden())
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .isInstanceOf(NotAuthorizedException.class))
        .andReturn();
  }

  protected MvcResult postRequestShouldFailWithNoAuthorization(String requestUrl, Object query)
      throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl)
            .content(mockMvcTestRule.json(query))
            .contentType(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().isForbidden())
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .isInstanceOf(NotAuthorizedException.class))
        .andReturn();
  }

  protected MvcResult deleteRequestShouldFailWithNoAuthorization(String requestUrl)
      throws Exception {
    final MockHttpServletRequestBuilder request =
        delete(requestUrl).accept(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().isForbidden())
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .isInstanceOf(NotAuthorizedException.class))
        .andReturn();
  }

  protected void assertErrorMessageContains(MvcResult mvcResult, String text) {
    assertThat(mvcResult.getResolvedException().getMessage()).contains(text);
  }

  protected void assertErrorMessageIsEqualTo(MvcResult mvcResult, String message) {
    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo(message);
  }

  protected void runArchiving(
      ProcessInstancesArchiverJob archiverJob, Callable<Void> esIndexRefresher) {
    try {
      int archived;
      int archivedTotal = 0;
      do {
        archived = archiverJob.archiveNextBatch().join();
        esIndexRefresher.call();
        archivedTotal += archived;
      } while (archived > 0);
      assertThat(archivedTotal).isGreaterThan(0);
    } catch (Exception e) {
      throw new RuntimeException("Error while archiving", e);
    }
  }

  protected void mockPartitionHolder(PartitionHolder partitionHolder) {
    final List<Integer> partitions = new ArrayList<>();
    partitions.add(1);
    when(partitionHolder.getPartitionIds()).thenReturn(partitions);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.OperateIntegrationTest.DEFAULT_USER;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.Permission;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.zeebe.PartitionHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
  properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
    OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
    "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"})
@WebAppConfiguration
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public abstract class OperateIntegrationTest {

  public static final String DEFAULT_USER = "testuser";

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  protected MockMvc mockMvc;

  protected OffsetDateTime testStartTime;

  @MockBean
  protected UserService userService;

  @Before
  public void before() {
    testStartTime = OffsetDateTime.now();
    mockMvc = mockMvcTestRule.getMockMvc();
    when(userService.getCurrentUser()).thenReturn(
        new UserDto().setUserId(DEFAULT_USER)
            .setPermissions(List.of(Permission.WRITE)));
  }

  protected MvcResult getRequest(String requestUrl) throws Exception {
    MockHttpServletRequestBuilder request = get(requestUrl).accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(mockMvcTestRule.getContentType()))
      .andReturn();

    return mvcResult;
  }

  protected MvcResult getRequestShouldFailWithException(String requestUrl, Class<? extends Exception> exceptionClass) throws Exception {
    MockHttpServletRequestBuilder request = get(requestUrl).accept(mockMvcTestRule.getContentType());

    return mockMvc.perform(request)
      .andExpect(status().is4xxClientError())
      .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
      .andReturn();
  }

  protected MvcResult postRequestShouldFailWithException(String requestUrl, Class<? extends Exception> exceptionClass) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
        .content("{}")
        .contentType(mockMvcTestRule.getContentType())
        .accept(mockMvcTestRule.getContentType());

    return mockMvc.perform(request)
      .andExpect(status().is4xxClientError())
      .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
      .andReturn();
  }

  protected MvcResult postRequest(String requestUrl, Object query) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

      return mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(mockMvcTestRule.getContentType()))
        .andReturn();
  }

  protected MvcResult postRequestThatShouldFail(String requestUrl, Object query) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request)
            .andExpect(status()
            .isBadRequest())
            .andReturn();
  }

  protected MvcResult postRequestThatShouldFail(String requestUrl, String stringContent) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
      .content(stringContent)
      .contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request)
            .andExpect(status()
            .isBadRequest())
            .andReturn();
  }

  protected MvcResult getRequestShouldFailWithNoAuthorization(String requestUrl) throws Exception {
    MockHttpServletRequestBuilder request = get(requestUrl).accept(mockMvcTestRule.getContentType());

    return mockMvc.perform(request)
        .andExpect(status().isForbidden())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(NotAuthorizedException.class))
        .andReturn();
  }

  protected MvcResult postRequestShouldFailWithNoAuthorization(String requestUrl, Object query) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl).content(mockMvcTestRule.json(query)).contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request)
        .andExpect(status().isForbidden())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(NotAuthorizedException.class))
        .andReturn();
  }

  protected void assertErrorMessageContains(MvcResult mvcResult, String text) {
    assertThat(mvcResult.getResolvedException().getMessage()).contains(text);
  }

  protected void assertErrorMessageIsEqualTo(MvcResult mvcResult, String message) {
    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo(message);
  }

  protected void runArchiving(ProcessInstancesArchiverJob archiverJob, Callable<Void> esIndexRefresher) {
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
    List<Integer> partitions = new ArrayList<>();
    partitions.add(1);
    when(partitionHolder.getPartitionIds()).thenReturn(partitions);
  }
}

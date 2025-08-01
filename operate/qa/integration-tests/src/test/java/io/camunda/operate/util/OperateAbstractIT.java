/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.TenantAccess;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      "spring.profiles.active=test,consolidated-auth",
      "camunda.security.authorizations.enabled=false"
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

  @MockBean protected CamundaAuthenticationProvider camundaAuthenticationProvider;

  @MockBean protected TenantService tenantService;

  @Before
  public void before() {
    testStartTime = OffsetDateTime.now();
    mockMvc = mockMvcTestRule.getMockMvc();
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(
            new CamundaAuthentication(
                DEFAULT_USER,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()));
    mockTenantResponse();
  }

  protected void mockTenantResponse() {
    doReturn(TenantAccess.wildcard(null)).when(tenantService).getAuthenticatedTenants();
  }

  protected MvcResult getRequest(final String requestUrl) throws Exception {
    return getRequest(requestUrl, mockMvcTestRule.getContentType());
  }

  protected MvcResult getRequest(final String requestUrl, final MediaType responseMediaType)
      throws Exception {
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
      final String requestUrl, final Class<? extends Exception> exceptionClass) throws Exception {
    final MockHttpServletRequestBuilder request =
        get(requestUrl).accept(mockMvcTestRule.getContentType());

    return mockMvc
        .perform(request)
        .andExpect(status().is4xxClientError())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
        .andReturn();
  }

  protected MvcResult postRequestShouldFailWithException(
      final String requestUrl, final Class<? extends Exception> exceptionClass) throws Exception {
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

  protected MvcResult postRequest(final String requestUrl, final Object query) throws Exception {
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

  protected MvcResult postRequestThatShouldFail(final String requestUrl, final Object query)
      throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl)
            .content(mockMvcTestRule.json(query))
            .contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn();
  }

  protected MvcResult postRequestThatShouldFail(final String requestUrl, final String stringContent)
      throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl).content(stringContent).contentType(mockMvcTestRule.getContentType());

    return mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn();
  }

  protected MvcResult getRequestShouldFailWithNoAuthorization(final String requestUrl)
      throws Exception {
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

  protected MvcResult postRequestShouldFailWithNoAuthorization(
      final String requestUrl, final Object query) throws Exception {
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

  protected MvcResult deleteRequestShouldFailWithNoAuthorization(final String requestUrl)
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

  protected void assertErrorMessageContains(final MvcResult mvcResult, final String text) {
    assertThat(mvcResult.getResolvedException().getMessage()).contains(text);
  }

  protected void assertErrorMessageIsEqualTo(final MvcResult mvcResult, final String message) {
    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo(message);
  }

  protected void mockPartitionHolder(final PartitionHolder partitionHolder) {
    final List<Integer> partitions = new ArrayList<>();
    partitions.add(1);
    when(partitionHolder.getPartitionIds()).thenReturn(partitions);
  }
}

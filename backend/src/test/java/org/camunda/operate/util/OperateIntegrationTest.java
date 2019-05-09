/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.camunda.operate.TestApplication;
import org.camunda.operate.property.OperateProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;


@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class},
  properties = {OperateProperties.PREFIX + ".startLoadingDataOnStartup = false",
    OperateProperties.PREFIX + ".elasticsearch.rolloverEnabled = false"})
@WebAppConfiguration
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ActiveProfiles("test")
public abstract class OperateIntegrationTest {
  
  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();
  

  protected MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }
  
  protected MvcResult getRequest(String requestUrl) throws Exception {
    MockHttpServletRequestBuilder request = get(requestUrl);
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    
    return mvcResult;
  }
  
  protected MvcResult postRequest(String requestUrl,Object query) throws Exception {
    MockHttpServletRequestBuilder request = post(requestUrl)
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

      return mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
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
  
  protected void assertErrorMessageContains(MvcResult mvcResult, String text) {
    assertThat(mvcResult.getResolvedException().getMessage()).contains(text);
  }
  
  protected void assertErrorMessageIsEqualTo(MvcResult mvcResult, String message) {
    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo(message);  
  }
  
}

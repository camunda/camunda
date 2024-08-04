/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Component
public class MockMvcManager {
  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  private final MediaType jsonContentType =
      new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype());

  public MockMvcManager(
      final WebApplicationContext webAppContext, final ObjectMapper objectMapper) {
    mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
    this.objectMapper = objectMapper;
  }

  public MvcResult postRequest(
      final String url, final Object dtoRequest, final Integer expectedStatus) throws Exception {
    final MockHttpServletRequestBuilder ope =
        post(url).content(objectMapper.writeValueAsString(dtoRequest)).contentType(jsonContentType);

    if (expectedStatus != null) {
      return mockMvc.perform(ope).andExpect(status().is(expectedStatus)).andReturn();
    } else {
      return mockMvc.perform(ope).andReturn();
    }
  }

  public MvcResult getRequestShouldFailWithException(
      final String requestUrl, final Class<? extends Exception> exceptionClass) throws Exception {
    final MockHttpServletRequestBuilder request = get(requestUrl).accept(jsonContentType);

    return mockMvc
        .perform(request)
        .andExpect(status().is4xxClientError())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
        .andReturn();
  }

  public MvcResult postRequestShouldFailWithException(
      final String requestUrl, final Class<? extends Exception> exceptionClass) throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl).content("{}").contentType(jsonContentType).accept(jsonContentType);

    return mockMvc
        .perform(request)
        .andExpect(status().is4xxClientError())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(exceptionClass))
        .andReturn();
  }
}

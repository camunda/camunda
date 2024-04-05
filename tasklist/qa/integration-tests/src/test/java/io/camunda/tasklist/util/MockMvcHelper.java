/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MockMvcHelper {
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  public MockMvcHelper(MockMvc mockMvc, ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  public MockHttpServletResponse doRequest(
      MockHttpServletRequestBuilder requestBuilder, Object requestBody) {
    try {
      if (requestBody != null) {
        requestBuilder
            .characterEncoding(StandardCharsets.UTF_8.name())
            .content(objectMapper.writeValueAsString(requestBody))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
      }

      return mockMvc.perform(requestBuilder).andDo(print()).andReturn().getResponse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public MockHttpServletResponse doRequest(MockHttpServletRequestBuilder requestBuilder) {
    return doRequest(requestBuilder, null);
  }
}

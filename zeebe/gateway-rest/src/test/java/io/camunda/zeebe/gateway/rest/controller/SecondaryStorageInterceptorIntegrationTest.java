/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(SpringJUnitExtension.class)
@SpringBootTest(
    classes = {
      io.camunda.zeebe.gateway.rest.config.WebMvcConfiguration.class,
      io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor.class,
      io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor.DatabaseProperties.class,
    })
@AutoConfigureWebMvc
@TestPropertySource(properties = {"camunda.database.type=none"})
public class SecondaryStorageInterceptorIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldReturn403ForEndpointRequiringSecondaryStorageWhenDisabled() throws Exception {
    // when/then
    final MvcResult result =
        mockMvc
            .perform(get("/v2/batch-operations/123"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andReturn();

    final String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("Secondary Storage Required");
    assertThat(responseBody).contains("headless mode");
    assertThat(responseBody).contains("database.type=none");
  }

  @Test
  void shouldReturn403ForSearchEndpointRequiringSecondaryStorageWhenDisabled() throws Exception {
    // when/then
    final MvcResult result =
        mockMvc
            .perform(
                post("/v2/batch-operations/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andReturn();

    final String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("Secondary Storage Required");
    assertThat(responseBody).contains("headless mode");
  }

  @Test
  void shouldReturn403ForProcessDefinitionEndpointWhenSecondaryStorageDisabled() throws Exception {
    // when/then
    final MvcResult result =
        mockMvc
            .perform(get("/v2/process-definitions/123"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andReturn();

    final String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("Secondary Storage Required");
  }

  @Test
  void shouldReturn403ForUserTaskEndpointWhenSecondaryStorageDisabled() throws Exception {
    // when/then
    final MvcResult result =
        mockMvc
            .perform(get("/v2/user-tasks/123"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andReturn();

    final String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("Secondary Storage Required");
  }

  // Note: Controllers that don't require secondary storage should work normally
  // This test would require a mock for those controllers, which would be
  // in separate test classes focused on those specific controllers
}
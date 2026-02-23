/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.identity.webapp.controllers.IdentityClientConfigController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IdentityClientConfigControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    final var controller = new IdentityClientConfigController();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldRedirectIdentityConfigToAdminConfig() throws Exception {
    mockMvc
        .perform(get("/identity/config.js"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "/admin/config.js"));
  }
}

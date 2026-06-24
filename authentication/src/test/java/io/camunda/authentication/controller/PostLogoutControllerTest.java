/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class PostLogoutControllerTest {

  private static final String POST_LOGOUT_REDIRECT_ATTRIBUTE = "postLogoutRedirect";
  private static final String POST_LOGOUT_URL = "https://camunda.test:443/post-logout";

  private final PostLogoutController controller = new PostLogoutController();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldRedirectToDefaultWhenSessionIsNotPresent() throws Exception {
    // when/then
    mockMvc
        .perform(get(POST_LOGOUT_URL))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void shouldFallbackToDefaultWhenNonStringAttribute() throws Exception {
    // when/then
    mockMvc
        .perform(get(POST_LOGOUT_URL).sessionAttr(POST_LOGOUT_REDIRECT_ATTRIBUTE, 111))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void shouldFallbackToDefaultWhenBlankStringAttribute() throws Exception {
    // when/then
    mockMvc
        .perform(get(POST_LOGOUT_URL).sessionAttr(POST_LOGOUT_REDIRECT_ATTRIBUTE, "   "))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void shouldFallbackToDefaultWhenWrongHostname() throws Exception {
    // when/then
    mockMvc
        .perform(
            get(POST_LOGOUT_URL)
                .sessionAttr(POST_LOGOUT_REDIRECT_ATTRIBUTE, "https://dangerous.site/some-page"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void shouldRedirectToPostLogoutRedirectWhenPresent() throws Exception {
    // when/then
    mockMvc
        .perform(
            get(POST_LOGOUT_URL)
                .sessionAttr(POST_LOGOUT_REDIRECT_ATTRIBUTE, "https://camunda.test/after-logout"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("https://camunda.test/after-logout"));
  }
}

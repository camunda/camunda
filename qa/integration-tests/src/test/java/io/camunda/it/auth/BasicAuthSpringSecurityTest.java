/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import java.util.List;
import org.apache.logging.log4j.util.Base64Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(
    classes = {CommonsModuleConfiguration.class},
    properties = {
      "spring.profiles.active=consolidated-auth",
      "camunda.security.authentication.method=basic",
      "camunda.security.authentication.unprotected-api=false"
    })
@WebAppConfiguration
@AutoConfigureMockMvc
public class BasicAuthSpringSecurityTest {
  private static final String USERNAME = "correct_username";
  private static final String PASSWORD = "correct_password";
  @Autowired PasswordEncoder passwordEncoder;
  @MockBean private UserServices userService;
  @MockBean private AuthorizationServices authorizationServices;
  @MockBean private RoleServices roleServices;
  @MockBean private TenantServices tenantServices;
  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws JsonProcessingException {
    // return user service when accessed with any authenticated user
    when(userService.withAuthentication(any(Authentication.class))).thenReturn(userService);
    when(userService.search(any()))
        .thenReturn(
            new SearchQueryResult<>(
                1,
                List.of(new UserEntity(1L, USERNAME, "name", "", passwordEncoder.encode(PASSWORD))),
                null,
                null));
    // mock services involved in authorization resolution to just return no results as we don't test
    // authorization here but just authentication
    when(tenantServices.getTenantsByMemberKey(anyLong())).thenReturn(List.of());
    when(authorizationServices.findAll(any())).thenReturn(List.of());
    when(roleServices.findAll(any())).thenReturn(List.of());
  }

  @Test
  void basicAuthWithValidCredentials() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.get("/v2/authentication/me")
            .accept("application/json")
            .header("Authorization", "Basic " + Base64Util.encode(USERNAME + ":" + PASSWORD));
    mockMvc.perform(request).andExpect(status().isOk());
  }

  @Test
  void basicAuthWithNoCredentials() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.get("/v2/authentication/me").accept("application/json");
    mockMvc.perform(request).andExpect(status().isUnauthorized());
  }

  @Test
  void basicAuthWithBadCredentials() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.get("/v2/authentication/me")
            .accept("application/json")
            .header(
                "Authorization", "Basic " + Base64Util.encode(USERNAME + ":" + PASSWORD + "Wrong"));
    mockMvc.perform(request).andExpect(status().isUnauthorized()).andReturn();
  }
}

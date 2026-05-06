/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestApiController {

  public static final String DEFAULT_RESPONSE = "I am dummy endpoint";
  public static final String DUMMY_OPERATE_INTERNAL_API_ENDPOINT = "/api/foo";
  public static final String DUMMY_V1_API_ENDPOINT = "/v1/foo";
  public static final String DUMMY_V2_API_ENDPOINT = "/v2/foo";
  public static final String DUMMY_V2_API_AUTH_ENDPOINT = "/v2/auth";
  public static final String DUMMY_WEBAPP_ENDPOINT = "/operate/decisions";
  public static final String DUMMY_UNPROTECTED_ENDPOINT = "/new/foo";
  public static final String DUMMY_UNHANDLED_ENDPOINT = "/non-existent-endpoint";

  private final CamundaAuthenticationProvider testAuthenticationProvider;

  public TestApiController(
      @Autowired(required = false) final CamundaAuthenticationProvider testAuthenticationProvider) {
    this.testAuthenticationProvider = testAuthenticationProvider;
  }

  @RequestMapping(DUMMY_OPERATE_INTERNAL_API_ENDPOINT)
  public @ResponseBody String dummyOperateInternalApiEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_V1_API_ENDPOINT)
  public @ResponseBody String dummyV1ApiEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_V2_API_ENDPOINT)
  public @ResponseBody String dummyV2ApiEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_V2_API_AUTH_ENDPOINT)
  public @ResponseBody String dummyV2ApiAuthEndpoint() {
    return testAuthenticationProvider != null
            && testAuthenticationProvider.getCamundaAuthentication() != null
        ? testAuthenticationProvider.getCamundaAuthentication().authenticatedUsername()
        : "None";
  }

  @RequestMapping(DUMMY_WEBAPP_ENDPOINT)
  public @ResponseBody String dummyWebappEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_UNPROTECTED_ENDPOINT)
  public @ResponseBody String dummyUnprotectedEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @PostMapping(DUMMY_V2_API_ENDPOINT)
  public @ResponseBody String dummyV2ApiPostEndpoint() {
    return DEFAULT_RESPONSE;
  }
}

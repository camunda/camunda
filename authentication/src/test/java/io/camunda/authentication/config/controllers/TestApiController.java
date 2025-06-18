/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestApiController {

  public static final String DUMMY_OPERATE_INTERNAL_API_ENDPOINT = "/api/foo";
  public static final String DUMMY_V1_API_ENDPOINT = "/v1/foo";
  public static final String DUMMY_V2_API_ENDPOINT = "/v2/foo";
  public static final String DUMMY_WEBAPP_ENDPOINT = "/decisions";
  public static final String DUMMY_UNPROTECTED_ENDPOINT = "/new/foo";

  @RequestMapping(DUMMY_OPERATE_INTERNAL_API_ENDPOINT)
  public @ResponseBody String dummyOperateInternalApiEndpoint() {
    return "yoooo";
  }

  @RequestMapping(DUMMY_V1_API_ENDPOINT)
  public @ResponseBody String dummyV1ApiEndpoint() {
    return "yoooo";
  }

  @RequestMapping(DUMMY_V2_API_ENDPOINT)
  public @ResponseBody String dummyV2ApiEndpoint() {
    return "yoooo";
  }

  @RequestMapping(DUMMY_WEBAPP_ENDPOINT)
  public @ResponseBody String dummyWebappEndpoint() {
    return "yoooo";
  }

  @RequestMapping(DUMMY_UNPROTECTED_ENDPOINT)
  public @ResponseBody String dummyUnprotectedEndpoint() {
    return "yoooo";
  }
}

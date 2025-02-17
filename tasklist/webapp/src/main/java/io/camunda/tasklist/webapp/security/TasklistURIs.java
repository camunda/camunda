/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.rest.ClientConfigRestService.CLIENT_CONFIG_RESOURCE;

import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

public final class TasklistURIs {

  public static final String ROOT_URL = "/tasklist";
  public static final String ROOT = ROOT_URL;
  public static final String ERROR_URL = "/error";
  public static final String REST_V1_API = "/v1/";
  public static final String REST_V1_EXTERNAL_API = "/v1/external/**";
  public static final String NEW_FORM = "/new/**";
  public static final String ALL_REST_VERSION_API = "/v*/**";
  public static final String TASKS_URL_V1 = "/v1/tasks";
  public static final String VARIABLES_URL_V1 = "/v1/variables";
  public static final String FORMS_URL_V1 = "/v1/forms";
  public static final String USERS_URL_V1 = "/v1/internal/users";
  public static final String DEV_UTIL_URL_V1 = "/v1/external/devUtil";
  public static final String PROCESSES_URL_V1 = "/v1/internal/processes";
  public static final String EXTERNAL_PROCESS_URL_V1 = "/v1/external/process";

  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String SSO_CALLBACK = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";
  public static final String IDENTITY_CALLBACK_URI = "/identity-callback";
  public static final String COOKIE_JSESSIONID = "TASKLIST-SESSION";
  public static final String START_PUBLIC_PROCESS = ROOT_URL + "/new/";
  public static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";

  private TasklistURIs() {}

  public static final RequestMatcher[] getAuthWhitelist(
      final HandlerMappingIntrospector introspector) {
    final RequestMatcher[] requestMatchers = {
      AntPathRequestMatcher.antMatcher("/webjars/**"),
      AntPathRequestMatcher.antMatcher(CLIENT_CONFIG_RESOURCE),
      new MvcRequestMatcher(introspector, ERROR_URL),
      AntPathRequestMatcher.antMatcher(NO_PERMISSION),
      AntPathRequestMatcher.antMatcher(LOGIN_RESOURCE),
      AntPathRequestMatcher.antMatcher(LOGOUT_RESOURCE),
      AntPathRequestMatcher.antMatcher(REST_V1_EXTERNAL_API),
      new MvcRequestMatcher(introspector, NEW_FORM),
      AntPathRequestMatcher.antMatcher("/v3/api-docs/**")
    };
    return requestMatchers;
  }

  // Used as constants class

}

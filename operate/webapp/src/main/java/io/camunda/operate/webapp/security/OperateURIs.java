/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

public final class OperateURIs {

  public static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";
  public static final String ROOT = "/operate";
  public static final String API = "/api/**";
  public static final String PUBLIC_API = "/v*/**";
  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String COOKIE_JSESSIONID = "OPERATE-SESSION";
  public static final String SSO_CALLBACK_URI = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";
  public static final String IDENTITY_CALLBACK_URI = "/identity-callback";
  public static final String[] AUTH_WHITELIST = {
    "/swagger-resources",
    "/swagger-resources/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/documentation",
    "/actuator/**",
    LOGIN_RESOURCE,
    SSO_CALLBACK_URI,
    NO_PERMISSION,
    LOGOUT_RESOURCE
  };

  // Used as constants class
  private OperateURIs() {}
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.rest.ClientConfigRestService.CLIENT_CONFIG_RESOURCE;

public final class TasklistURIs {

  public static final String ROOT_URL = "/";
  public static final String ROOT = ROOT_URL;
  public static final String ERROR_URL = "/error";
  public static final String GRAPHQL_URL = "/graphql";

  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String SSO_CALLBACK = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";
  public static final String IDENTITY_CALLBACK_URI = "/identity-callback";

  public static final String REQUESTED_URL = "requestedUrl";

  public static final String IAM_CALLBACK_URI = "/iam-callback";
  public static final String IAM_LOGOUT_CALLBACK_URI = "/iam-logout-callback";
  public static final String COOKIE_JSESSIONID = "TASKLIST-SESSION";

  public static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";

  public static final String[] AUTH_WHITELIST = {
    "/webjars/**", CLIENT_CONFIG_RESOURCE, ERROR_URL, NO_PERMISSION, LOGIN_RESOURCE, LOGOUT_RESOURCE
  };
  // Used as constants class
  private TasklistURIs() {}
}

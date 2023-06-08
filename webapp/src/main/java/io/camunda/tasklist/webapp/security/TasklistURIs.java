/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.rest.ClientConfigRestService.CLIENT_CONFIG_RESOURCE;

public final class TasklistURIs {

  public static final String ROOT_URL = "/";
  public static final String ROOT = ROOT_URL;
  public static final String ERROR_URL = "/error";
  public static final String GRAPHQL_URL = "/graphql";
  public static final String REST_V1_API = "/v1/";
  public static final String REST_V1_EXTERNAL_API = "/v1/external/**";
  public static final String NEW_FORM = "/new/**";
  public static final String ALL_REST_V1_API = "/v1/**";
  public static final String TASKS_URL_V1 = "/v1/tasks";
  public static final String VARIABLES_URL_V1 = "/v1/variables";
  public static final String FORMS_URL_V1 = "/v1/forms";
  public static final String USERS_URL_V1 = "/v1/internal/users";
  public static final String PROCESSES_URL_V1 = "/v1/internal/processes";
  public static final String EXTERNAL_PROCESS_URL_V1 = "/v1/external/process";

  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String SSO_CALLBACK = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";
  public static final String IDENTITY_CALLBACK_URI = "/identity-callback";
  public static final String REQUESTED_URL = "requestedUrl";
  public static final String COOKIE_JSESSIONID = "TASKLIST-SESSION";
  public static final String START_PUBLIC_PROCESS = "/new/";
  public static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";

  public static final String[] AUTH_WHITELIST = {
    "/webjars/**",
    CLIENT_CONFIG_RESOURCE,
    ERROR_URL,
    NO_PERMISSION,
    LOGIN_RESOURCE,
    LOGOUT_RESOURCE,
    REST_V1_EXTERNAL_API,
    NEW_FORM
  };

  // Used as constants class
  private TasklistURIs() {}
}

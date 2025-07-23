/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

public final class TasklistURIs {

  public static final String ROOT_URL = "/tasklist";
  public static final String REST_V1_API = "/v1/";
  public static final String TASKS_URL_V1 = "/v1/tasks";
  public static final String VARIABLES_URL_V1 = "/v1/variables";
  public static final String FORMS_URL_V1 = "/v1/forms";
  public static final String DEV_UTIL_URL_V1 = "/v1/external/devUtil";
  public static final String PROCESSES_URL_V1 = "/v1/internal/processes";
  public static final String EXTERNAL_PROCESS_URL_V1 = "/v1/external/process";

  public static final String COOKIE_JSESSIONID = "TASKLIST-SESSION";
  public static final String START_PUBLIC_PROCESS = ROOT_URL + "/new/";

  private TasklistURIs() {}
}

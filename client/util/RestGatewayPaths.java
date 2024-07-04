/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.util;

import static io.camunda.zeebe.client.impl.http.HttpClientFactory.REST_API_PATH;

public class RestGatewayPaths {

  private static final String URL_TOPOLOGY = REST_API_PATH + "/topology";
  private static final String URL_JOB_ACTIVATION = REST_API_PATH + "/jobs/activation";
  private static final String URL_USER_TASK_ASSIGNMENT =
      REST_API_PATH + "/user-tasks/%s/assignment";
  private static final String URL_USER_TASK_COMPLETION =
      REST_API_PATH + "/user-tasks/%s/completion";
  private static final String URL_USER_TASK_UNASSIGNMENT =
      REST_API_PATH + "/user-tasks/%s/assignee";
  private static final String URL_USER_TASK_UPDATE = REST_API_PATH + "/user-tasks/%s";

  /**
   * @return the topology request URL
   */
  public static String getTopologyUrl() {
    return URL_TOPOLOGY;
  }

  /**
   * @return the job activation request URL
   */
  public static String getJobActivationUrl() {
    return URL_JOB_ACTIVATION;
  }

  /**
   * @param userTaskKey the user task key to get the URL for
   * @return the user task assignment request URL
   */
  public static String getUserTaskAssignmentUrl(final long userTaskKey) {
    return String.format(URL_USER_TASK_ASSIGNMENT, userTaskKey);
  }

  /**
   * @param userTaskKey the user task key to get the URL for
   * @return the user task completion request URL
   */
  public static String getUserTaskCompletionUrl(final long userTaskKey) {
    return String.format(URL_USER_TASK_COMPLETION, userTaskKey);
  }

  /**
   * @param userTaskKey the user task key to get the URL for
   * @return the user task unassignment request URL
   */
  public static String getUserTaskUnassignmentUrl(final long userTaskKey) {
    return String.format(URL_USER_TASK_UNASSIGNMENT, userTaskKey);
  }

  /**
   * @param userTaskKey the user task key to get the URL for
   * @return the user task update request URL
   */
  public static String getUserTaskUpdateUrl(final long userTaskKey) {
    return String.format(URL_USER_TASK_UPDATE, userTaskKey);
  }
}

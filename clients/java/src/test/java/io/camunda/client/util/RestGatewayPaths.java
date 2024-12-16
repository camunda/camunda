/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.util;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;

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
  private static final String URL_MESSAGE_PUBLICATION = REST_API_PATH + "/messages/publication";
  private static final String URL_CLOCK_PIN = REST_API_PATH + "/clock";
  private static final String URL_CLOCK_RESET = REST_API_PATH + "/clock/reset";
  private static final String URL_INCIDENT_RESOLUTION = REST_API_PATH + "/incidents/%s/resolution";
  private static final String URL_CANCEL_PROCESS =
      REST_API_PATH + "/process-instances/%s/cancellation";
  private static final String URL_BROADCAST_SIGNAL = REST_API_PATH + "/signals/broadcast";
  private static final String URL_EVALUATE_DECISION =
      REST_API_PATH + "/decision-definitions/evaluation";
  private static final String URL_CREATE_PROCESS_INSTANCE = REST_API_PATH + "/process-instances";
  private static final String URL_DEPLOYMENTS_URL = REST_API_PATH + "/deployments";

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

  /**
   * @return message publication request URL
   */
  public static String getMessagePublicationUrl() {
    return URL_MESSAGE_PUBLICATION;
  }

  /**
   * @return pin clock request URL
   */
  public static String getClockPinUrl() {
    return URL_CLOCK_PIN;
  }

  /**
   * @return reset clock request URL
   */
  public static String getClockResetUrl() {
    return URL_CLOCK_RESET;
  }

  /**
   * @param incidentKey the key of the corresponding incident to get the URL for
   * @return resolve incident request URL
   */
  public static String getIncidentResolutionUrl(final long incidentKey) {
    return String.format(URL_INCIDENT_RESOLUTION, incidentKey);
  }

  public static String getCancelProcessUrl(final long processInstanceKey) {
    return String.format(URL_CANCEL_PROCESS, processInstanceKey);
  }

  public static String getBroadcastSignalUrl() {
    return URL_BROADCAST_SIGNAL;
  }

  public static String getEvaluateDecisionUrl() {
    return URL_EVALUATE_DECISION;
  }

  /**
   * @return create process instance request URL
   */
  public static String getCreateProcessInstanceUrl() {
    return URL_CREATE_PROCESS_INSTANCE;
  }

  public static String getDeploymentsUrl() {
    return URL_DEPLOYMENTS_URL;
  }
}

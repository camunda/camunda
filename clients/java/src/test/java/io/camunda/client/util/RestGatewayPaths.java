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

  private static final String URL_AUTHORIZATION = REST_API_PATH + "/authorizations/%s";
  private static final String URL_AUTHORIZATIONS = REST_API_PATH + "/authorizations";
  private static final String URL_BATCH_OPERATION = REST_API_PATH + "/batch-operations/%s";
  private static final String URL_CLOCK_PIN = REST_API_PATH + "/clock";
  private static final String URL_CLOCK_RESET = REST_API_PATH + "/clock/reset";
  private static final String URL_DECISION_DEFINITION = REST_API_PATH + "/decision-definitions/%s";
  private static final String URL_DECISION_EVALUATION =
      REST_API_PATH + "/decision-definitions/evaluation";
  private static final String URL_DECISION_INSTANCE = REST_API_PATH + "/decision-instances/%s";
  private static final String URL_DECISION_REQUIREMENTS =
      REST_API_PATH + "/decision-requirements/%s";
  private static final String URL_DEPLOYMENTS = REST_API_PATH + "/deployments";
  private static final String URL_ELEMENT_INSTANCE = REST_API_PATH + "/element-instances/%s";
  private static final String URL_GROUP = REST_API_PATH + "/groups/%s";
  private static final String URL_GROUPS = REST_API_PATH + "/groups";
  private static final String URL_INCIDENT = REST_API_PATH + "/incidents/%s";
  private static final String URL_INCIDENT_RESOLUTION = REST_API_PATH + "/incidents/%s/resolution";
  private static final String URL_JOB_ACTIVATION = REST_API_PATH + "/jobs/activation";
  private static final String URL_MAPPING_RULES = REST_API_PATH + "/mapping-rules";
  private static final String URL_MAPPING_RULE = URL_MAPPING_RULES + "/%s";
  private static final String URL_MESSAGE_PUBLICATION = REST_API_PATH + "/messages/publication";
  private static final String URL_MESSAGE_CORRELATION = REST_API_PATH + "/messages/correlation";
  private static final String URL_PROCESS_DEFINITION = REST_API_PATH + "/process-definitions/%s";
  private static final String URL_PROCESS_DEFINITION_FORM =
      REST_API_PATH + "/process-definitions/%s/form";
  private static final String URL_PROCESS_INSTANCE = REST_API_PATH + "/process-instances/%s";
  private static final String URL_PROCESS_INSTANCE_CANCELLATION =
      REST_API_PATH + "/process-instances/%s/cancellation";
  private static final String URL_PROCESS_INSTANCE_CALL_HIERARCHY =
      REST_API_PATH + "/process-instances/%s/call-hierarchy";
  private static final String URL_PROCESS_INSTANCE_SEQUENCE_FLOWS =
      REST_API_PATH + "/process-instances/%s/sequence-flows";
  private static final String URL_PROCESS_INSTANCES = REST_API_PATH + "/process-instances";
  private static final String URL_PROCESS_INSTANCES_CANCELLATION =
      REST_API_PATH + "/process-instances/cancellation";
  private static final String URL_PROCESS_INSTANCES_INCIDENT_RESOLUTION =
      REST_API_PATH + "/process-instances/incident-resolution";
  private static final String URL_PROCESS_INSTANCES_MIGRATION =
      REST_API_PATH + "/process-instances/migration";
  private static final String URL_PROCESS_INSTANCES_MODIFICATION =
      REST_API_PATH + "/process-instances/modification";
  private static final String URL_ROLE = REST_API_PATH + "/roles/%s";
  private static final String URL_ROLES = REST_API_PATH + "/roles";
  private static final String URL_SIGNAL_BROADCAST = REST_API_PATH + "/signals/broadcast";
  private static final String URL_TENANT = REST_API_PATH + "/tenants/%s";
  private static final String URL_TENANTS = REST_API_PATH + "/tenants";
  private static final String URL_TOPOLOGY = REST_API_PATH + "/topology";
  private static final String URL_STATUS = REST_API_PATH + "/status";
  private static final String URL_USAGE_METRICS = REST_API_PATH + "/system/usage-metrics";
  private static final String URL_USER = REST_API_PATH + "/users/%s";
  private static final String URL_USERS = REST_API_PATH + "/users";
  private static final String URL_USER_TASK = REST_API_PATH + "/user-tasks/%s";
  private static final String URL_USER_TASK_ASSIGNMENT =
      REST_API_PATH + "/user-tasks/%s/assignment";
  private static final String URL_USER_TASK_COMPLETION =
      REST_API_PATH + "/user-tasks/%s/completion";
  private static final String URL_USER_TASK_FORM = REST_API_PATH + "/user-tasks/%s/form";
  private static final String URL_USER_TASK_UNASSIGNMENT =
      REST_API_PATH + "/user-tasks/%s/assignee";
  private static final String URL_USER_TASK_UPDATE = REST_API_PATH + "/user-tasks/%s";
  private static final String URL_VARIABLE = REST_API_PATH + "/variables/%s";

  /**
   * @return the topology request URL
   */
  public static String getTopologyUrl() {
    return URL_TOPOLOGY;
  }

  /**
   * @return the status request URL
   */
  public static String getStatusUrl() {
    return URL_STATUS;
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
    return String.format(URL_PROCESS_INSTANCE_CANCELLATION, processInstanceKey);
  }

  public static String getBroadcastSignalUrl() {
    return URL_SIGNAL_BROADCAST;
  }

  public static String getEvaluateDecisionUrl() {
    return URL_DECISION_EVALUATION;
  }

  /**
   * @return process instances request URL
   */
  public static String getProcessInstancesUrl() {
    return URL_PROCESS_INSTANCES;
  }

  public static String getDeploymentsUrl() {
    return URL_DEPLOYMENTS;
  }

  public static String getUsageMetricsUrl() {
    return URL_USAGE_METRICS;
  }

  public static String getDecisionDefinitionUrl(final long decisionDefinitionKey) {
    return String.format(URL_DECISION_DEFINITION, decisionDefinitionKey);
  }

  public static String getCreateTenantUrl() {
    return URL_TENANTS;
  }

  public static String getCreateUserUrl() {
    return URL_USERS;
  }

  public static String getMessageCorrelationUrl() {
    return URL_MESSAGE_CORRELATION;
  }

  public static String getProcessInstanceCallHierarchyUrl(final long processInstanceKey) {
    return String.format(URL_PROCESS_INSTANCE_CALL_HIERARCHY, processInstanceKey);
  }

  public static String getProcessInstancesUrl(final long processInstanceKey) {
    return String.format(URL_PROCESS_INSTANCE, processInstanceKey);
  }

  public static String getGroupUrl(final String groupId) {
    return String.format(URL_GROUP, groupId);
  }

  public static String getProcessDefinitionFormUrl(final long processDefinitionKey) {
    return String.format(URL_PROCESS_DEFINITION_FORM, processDefinitionKey);
  }

  public static String getProcessDefinitionUrl(final long processDefinitionKey) {
    return String.format(URL_PROCESS_DEFINITION, processDefinitionKey);
  }

  public static String getUserUrl(final String username) {
    return String.format(URL_USER, username);
  }

  public static String getMappingRulesUrl() {
    return URL_MAPPING_RULES;
  }

  public static String getMappingRuleUrl(final String mappingRuleId) {
    return String.format(URL_MAPPING_RULE, mappingRuleId);
  }

  public static String getGroupsUrl() {
    return URL_GROUPS;
  }

  public static String getProcessInstancesCancelUrl() {
    return URL_PROCESS_INSTANCES_CANCELLATION;
  }

  public static String getProcessInstancesIncidentResolutionUrl() {
    return URL_PROCESS_INSTANCES_INCIDENT_RESOLUTION;
  }

  public static String getProcessInstancesMigrateUrl() {
    return URL_PROCESS_INSTANCES_MIGRATION;
  }

  public static String getProcessInstancesModifyUrl() {
    return URL_PROCESS_INSTANCES_MODIFICATION;
  }

  public static String getAuthorizationsUrl() {
    return URL_AUTHORIZATIONS;
  }

  public static String getTenantUrl(final String tenantId) {
    return String.format(URL_TENANT, tenantId);
  }

  public static String getRoleUrl(final String roleId) {
    return String.format(URL_ROLE, roleId);
  }

  public static String getDecisionRequirementsUrl(final long decisionRequirementsKey) {
    return String.format(URL_DECISION_REQUIREMENTS, decisionRequirementsKey);
  }

  public static String getIncidentUrl(final long incidentKey) {
    return String.format(URL_INCIDENT, incidentKey);
  }

  public static String getDecisionInstanceUrl(final String decisionInstanceId) {
    return String.format(URL_DECISION_INSTANCE, decisionInstanceId);
  }

  public static String getUserTaskFormUrl(final long userTaskKey) {
    return String.format(URL_USER_TASK_FORM, userTaskKey);
  }

  public static String getProcessInstanceSequenceFlowsUrl(final long processInstanceKey) {
    return String.format(URL_PROCESS_INSTANCE_SEQUENCE_FLOWS, processInstanceKey);
  }

  public static String getBatchOperationUrl(final String batchOperationKey) {
    return String.format(URL_BATCH_OPERATION, batchOperationKey);
  }

  public static String getVariableUrl(final long variableKey) {
    return String.format(URL_VARIABLE, variableKey);
  }

  public static String getRolesUrl() {
    return URL_ROLES;
  }

  public static String getAuthorizationUrl(final long authorizationKey) {
    return String.format(URL_AUTHORIZATION, authorizationKey);
  }

  public static String getUserTaskUrl(final long userTaskKey) {
    return String.format(URL_USER_TASK, userTaskKey);
  }

  public static String getElementInstanceUrl(final long elementInstanceKey) {
    return String.format(URL_ELEMENT_INSTANCE, elementInstanceKey);
  }
}

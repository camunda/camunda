/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.importing;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EngineConstants {

  public static final String MAX_RESULTS_TO_RETURN = "maxResults";
  public static final String INDEX_OF_FIRST_RESULT = "firstResult";

  public static final String SORT_BY = "sortBy";
  public static final String SORT_ORDER = "sortOrder";
  public static final String SORT_ORDER_ASC = "asc";

  public static final String ID = "id";

  public static final String FINISHED_AFTER = "finishedAfter";
  public static final String FINISHED_AT = "finishedAt";

  public static final String DEPLOYED_AFTER = "deployedAfter";
  public static final String DEPLOYED_AT = "deployedAt";

  public static final String STARTED_AFTER = "startedAfter";
  public static final String STARTED_AT = "startedAt";

  public static final String CREATED_AFTER = "createdAfter";
  public static final String CREATED_AT = "createdAt";

  public static final String OCCURRED_AFTER = "occurredAfter";
  public static final String OCCURRED_AT = "occurredAt";

  public static final String MEMBER = "member";
  public static final String MEMBER_OF_GROUP = "memberOfGroup";

  public static final String USER_ID_IN = "userIdIn";
  public static final String GROUP_ID_IN = "groupIdIn";

  // endpoints in the engines to fetch the data for Optimize import
  public static final String VARIABLE_UPDATE_ENDPOINT = "/optimize/variable-update";
  public static final String COMPLETED_ACTIVITY_INSTANCE_ENDPOINT = "/optimize/activity-instance/completed";
  public static final String RUNNING_ACTIVITY_INSTANCE_ENDPOINT = "/optimize/activity-instance/running";
  public static final String OPEN_INCIDENT_ENDPOINT = "/optimize/incident/open";
  public static final String COMPLETED_INCIDENT_ENDPOINT = "/optimize/incident/completed";
  public static final String COMPLETED_PROCESS_INSTANCE_ENDPOINT = "/optimize/process-instance/completed";
  public static final String RUNNING_PROCESS_INSTANCE_ENDPOINT = "/optimize/process-instance/running";
  public static final String DECISION_INSTANCE_ENDPOINT = "/optimize/decision-instance";
  public static final String COMPLETED_USER_TASK_INSTANCE_ENDPOINT = "/optimize/task-instance/completed";
  public static final String RUNNING_USER_TASK_INSTANCE_ENDPOINT = "/optimize/task-instance/running";
  public static final String IDENTITY_LINK_LOG_ENDPOINT = "/optimize/identity-link-log";
  public static final String USER_OPERATION_LOG_ENDPOINT = "/optimize/user-operation";

  // native engine endpoints
  public static final String TENANT_ENDPOINT = "/tenant";
  public static final String VERSION_ENDPOINT = "/version";
  public static final String PROCESS_INSTANCE_ENDPOINT_TEMPLATE = "/history/process-instance/{id}";
  public static final String PROCESS_DEFINITION_ENDPOINT = "/process-definition";
  public static final String PROCESS_DEFINITION_ENDPOINT_TEMPLATE = "/process-definition/{id}";
  public static final String PROCESS_DEFINITION_XML_ENDPOINT_TEMPLATE = "/process-definition/{id}/xml";
  public static final String DECISION_DEFINITION_ENDPOINT = "/decision-definition";
  public static final String DECISION_DEFINITION_ENDPOINT_TEMPLATE = "/decision-definition/{id}";
  public static final String DECISION_DEFINITION_XML_ENDPOINT_TEMPLATE = "/decision-definition/{id}/xml";
  public static final String DEPLOYMENT_ENDPOINT_TEMPLATE = "/deployment/{id}";
  public static final String USER_VALIDATION_ENDPOINT = "/identity/verify";
  public static final String AUTHORIZATION_ENDPOINT = "/authorization";
  public static final String GROUP_ENDPOINT = "/group";
  public static final String GROUP_BY_ID_ENDPOINT_TEMPLATE = "/group/{id}";
  public static final String USER_ENDPOINT = "/user";
  public static final String USER_BY_ID_ENDPOINT_TEMPLATE = "/user/{id}/profile";
  public static final String USER_COUNT_ENDPOINT = "/user/count";

  public static final String ALL_PERMISSION = "ALL";
  public static final String ACCESS_PERMISSION = "ACCESS";
  public static final String READ_HISTORY_PERMISSION = "READ_HISTORY";
  public static final String READ_PERMISSION = "READ";

  public static final String RESOURCE_TYPE = "resourceType";
  public static final int RESOURCE_TYPE_APPLICATION = 0;
  public static final int RESOURCE_TYPE_DEPLOYMENT = 9;
  public static final int RESOURCE_TYPE_PROCESS_DEFINITION = 6;
  public static final int RESOURCE_TYPE_DECISION_DEFINITION = 10;
  public static final int RESOURCE_TYPE_USER = 1;
  public static final int RESOURCE_TYPE_GROUP = 2;
  public static final int RESOURCE_TYPE_AUTHORIZATION = 4;
  public static final int RESOURCE_TYPE_TENANT = 11;

  public static final int AUTHORIZATION_TYPE_GLOBAL = 0;
  public static final int AUTHORIZATION_TYPE_GRANT = 1;
  public static final int AUTHORIZATION_TYPE_REVOKE = 2;


  public static final String OPTIMIZE_APPLICATION_RESOURCE_ID = "optimize";
  public static final String ALL_RESOURCES_RESOURCE_ID = "*";

  public static final String IDENTITY_LINK_OPERATION_ADD = "add";
  public static final String IDENTITY_LINK_OPERATION_DELETE = "delete";

  public static final String SUSPEND_PROCESS_INSTANCE_OPERATION = "Suspend";
  public static final String ACTIVATE_PROCESS_INSTANCE_OPERATION = "Activate";
  public static final String PROCESS_INSTANCE_ENTITY_TYPE = "ProcessInstance";
  public static final String SUSPEND_PROCESS_DEFINITION_OPERATION = "SuspendProcessDefinition";
  public static final String ACTIVATE_PROCESS_DEFINITION_OPERATION = "ActivateProcessDefinition";
  public static final String INCL_INSTANCES_IN_DEFINITION_SUSPENSION_FIELD = "includeProcessInstances";
  public static final String SUSPEND_VIA_BATCH_OPERATION_TYPE = "SuspendJob";
  public static final String ACTIVATE_VIA_BATCH_OPERATION_TYPE = "ActivateJob";

  // incident type
  public static final String FAILED_JOB_INCIDENT_TYPE = "failedJob";
  public static final String FAILED_EXTERNAL_TASK_INCIDENT_TYPE = "failedExternalTask";

  // variable types
  public static final String VARIABLE_TYPE_OBJECT = "Object";
  public static final String VARIABLE_TYPE_JSON = "Json";
  public static final String VARIABLE_SERIALIZATION_DATA_FORMAT = "serializationDataFormat";

  // flownode types, relates to engine ActivityTypes enum
  public static final String FLOW_NODE_TYPE_USER_TASK = "userTask";
  public static final String FLOW_NODE_TYPE_MI_BODY = "multiInstanceBody";

}

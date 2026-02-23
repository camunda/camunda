/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiDelete, apiPost } from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export const AUTHORIZATIONS_ENDPOINT = "/authorizations";

export enum PermissionType {
  ACCESS = "ACCESS",
  CREATE = "CREATE",
  CREATE_PROCESS_INSTANCE = "CREATE_PROCESS_INSTANCE",
  CREATE_DECISION_INSTANCE = "CREATE_DECISION_INSTANCE",
  CREATE_TASK_LISTENER = "CREATE_TASK_LISTENER",
  EVALUATE = "EVALUATE",
  READ = "READ",
  READ_PROCESS_INSTANCE = "READ_PROCESS_INSTANCE",
  READ_USER_TASK = "READ_USER_TASK",
  READ_DECISION_INSTANCE = "READ_DECISION_INSTANCE",
  READ_PROCESS_DEFINITION = "READ_PROCESS_DEFINITION",
  READ_DECISION_DEFINITION = "READ_DECISION_DEFINITION",
  READ_TASK_LISTENER = "READ_TASK_LISTENER",
  UPDATE = "UPDATE",
  UPDATE_PROCESS_INSTANCE = "UPDATE_PROCESS_INSTANCE",
  UPDATE_USER_TASK = "UPDATE_USER_TASK",
  UPDATE_TASK_LISTENER = "UPDATE_TASK_LISTENER",
  DELETE = "DELETE",
  DELETE_PROCESS = "DELETE_PROCESS",
  DELETE_DRD = "DELETE_DRD",
  DELETE_FORM = "DELETE_FORM",
  DELETE_RESOURCE = "DELETE_RESOURCE",
  DELETE_PROCESS_INSTANCE = "DELETE_PROCESS_INSTANCE",
  DELETE_DECISION_INSTANCE = "DELETE_DECISION_INSTANCE",
  DELETE_TASK_LISTENER = "DELETE_TASK_LISTENER",
  CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_RESOLVE_INCIDENT = "CREATE_BATCH_OPERATION_RESOLVE_INCIDENT",
  CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE = "CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE",
  CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION = "CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION",
  CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION = "CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION",
  CANCEL_PROCESS_INSTANCE = "CANCEL_PROCESS_INSTANCE",
  MODIFY_PROCESS_INSTANCE = "MODIFY_PROCESS_INSTANCE",
  READ_USAGE_METRIC = "READ_USAGE_METRIC",
  READ_JOB_METRIC = "READ_JOB_METRIC",
  COMPLETE = "COMPLETE",
  COMPLETE_USER_TASK = "COMPLETE_USER_TASK",
  CLAIM = "CLAIM",
  CLAIM_USER_TASK = "CLAIM_USER_TASK",
}

export type PermissionTypes = keyof typeof PermissionType;

export enum OwnerType {
  "USER" = "USER",
  "ROLE" = "ROLE",
  "GROUP" = "GROUP",
  "MAPPING_RULE" = "MAPPING_RULE",
  "CLIENT" = "CLIENT",
}

export enum ResourceType {
  AUDIT_LOG = "AUDIT_LOG",
  AUTHORIZATION = "AUTHORIZATION",
  BATCH = "BATCH",
  CLUSTER_VARIABLE = "CLUSTER_VARIABLE",
  COMPONENT = "COMPONENT",
  DECISION_DEFINITION = "DECISION_DEFINITION",
  DECISION_REQUIREMENTS_DEFINITION = "DECISION_REQUIREMENTS_DEFINITION",
  DOCUMENT = "DOCUMENT",
  EXPRESSION = "EXPRESSION",
  GLOBAL_LISTENER = "GLOBAL_LISTENER",
  GROUP = "GROUP",
  MAPPING_RULE = "MAPPING_RULE",
  MESSAGE = "MESSAGE",
  USER_TASK = "USER_TASK",
  PROCESS_DEFINITION = "PROCESS_DEFINITION",
  RESOURCE = "RESOURCE",
  ROLE = "ROLE",
  SYSTEM = "SYSTEM",
  TENANT = "TENANT",
  USER = "USER",
}

type BaseAuthorization = {
  authorizationKey: string;
  ownerId: string;
  ownerType: OwnerType;
  permissionTypes: readonly PermissionTypes[];
};

export enum ResourcePropertyName {
  assignee = "assignee",
  candidateGroups = "candidateGroups",
  candidateUsers = "candidateUsers",
}

export type TaskAuthorization = BaseAuthorization & {
  resourceType: ResourceType.USER_TASK;
  resourcePropertyName: ResourcePropertyName;
};

export type GeneralAuthorization = BaseAuthorization & {
  resourceType: Exclude<ResourceType, ResourceType.USER_TASK>;
  resourceId: string;
};

export type Authorization = TaskAuthorization | GeneralAuthorization;

export type NewAuthorization =
  | Omit<TaskAuthorization, "authorizationKey">
  | Omit<GeneralAuthorization, "authorizationKey">;

export enum PatchAuthorizationAction {
  ADD = "ADD",
  REMOVE = "REMOVE",
}

export type searchAuthorizationsParams = {
  filter: {
    resourceType: string;
  };
};

export const searchAuthorization: ApiDefinition<
  SearchResponse<Authorization>,
  searchAuthorizationsParams
> = (param) => apiPost(`${AUTHORIZATIONS_ENDPOINT}/search`, param);

export const createAuthorization: ApiDefinition<undefined, NewAuthorization> = (
  authorization,
) => apiPost(AUTHORIZATIONS_ENDPOINT, authorization);

export type DeleteAuthorizationParams = {
  authorizationKey: string;
};

export const deleteAuthorization: ApiDefinition<
  undefined,
  DeleteAuthorizationParams
> = ({ authorizationKey }) =>
  apiDelete(`${AUTHORIZATIONS_ENDPOINT}/${authorizationKey}`);

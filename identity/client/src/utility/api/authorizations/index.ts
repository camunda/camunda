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
  READ = "READ",
  READ_PROCESS_INSTANCE = "READ_PROCESS_INSTANCE",
  READ_USER_TASK = "READ_USER_TASK",
  READ_DECISION_INSTANCE = "READ_DECISION_INSTANCE",
  READ_PROCESS_DEFINITION = "READ_PROCESS_DEFINITION",
  READ_DECISION_DEFINITION = "READ_DECISION_DEFINITION",
  UPDATE = "UPDATE",
  UPDATE_PROCESS_INSTANCE = "UPDATE_PROCESS_INSTANCE",
  UPDATE_USER_TASK = "UPDATE_USER_TASK",
  DELETE = "DELETE",
  DELETE_PROCESS = "DELETE_PROCESS",
  DELETE_DRD = "DELETE_DRD",
  DELETE_FORM = "DELETE_FORM",
  DELETE_PROCESS_INSTANCE = "DELETE_PROCESS_INSTANCE",
  DELETE_DECISION_INSTANCE = "DELETE_DECISION_INSTANCE",
  CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_RESOLVE_INCIDENT = "CREATE_BATCH_OPERATION_RESOLVE_INCIDENT",
  CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE = "CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE",
  CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE = "CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE",
  CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION = "CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION",
  CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION = "CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION",
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
  APPLICATION = "APPLICATION",
  AUTHORIZATION = "AUTHORIZATION",
  BATCH = "BATCH",
  DECISION_DEFINITION = "DECISION_DEFINITION",
  DECISION_REQUIREMENTS_DEFINITION = "DECISION_REQUIREMENTS_DEFINITION",
  DOCUMENT = "DOCUMENT",
  GROUP = "GROUP",
  MAPPING_RULE = "MAPPING_RULE",
  MESSAGE = "MESSAGE",
  PROCESS_DEFINITION = "PROCESS_DEFINITION",
  RESOURCE = "RESOURCE",
  ROLE = "ROLE",
  SYSTEM = "SYSTEM",
  TENANT = "TENANT",
  USER = "USER",
}

export type Authorization = {
  authorizationKey: string;
  ownerId: string;
  ownerType: keyof typeof OwnerType;
  resourceId: string;
  resourceType: string;
  permissionTypes: readonly PermissionTypes[];
};

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

export const createAuthorization: ApiDefinition<
  undefined,
  Omit<Authorization, "authorizationKey">
> = (authorization) => apiPost(AUTHORIZATIONS_ENDPOINT, authorization);

export type DeleteAuthorizationParams = {
  authorizationKey: string;
};

export const deleteAuthorization: ApiDefinition<
  undefined,
  DeleteAuthorizationParams
> = ({ authorizationKey }) =>
  apiDelete(`${AUTHORIZATIONS_ENDPOINT}/${authorizationKey}`);

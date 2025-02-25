/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { EntityData } from "src/components/entityList/EntityList";
import {
  ApiDefinition,
  apiDelete,
  apiPatch,
  apiPost,
  pathBuilder,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

const path = pathBuilder("/authorizations");
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
}

export type PermissionTypes = keyof typeof PermissionType;

export enum OwnerType {
  "USER" = "USER",
  "ROLE" = "ROLE",
  "GROUP" = "GROUP",
  "MAPPING" = "MAPPING",
  "UNSPECIFIED" = "UNSPECIFIED",
}

export enum ResourceType {
  APPLICATION = "APPLICATION",
  AUTHORIZATION = "AUTHORIZATION",
  BATCH = "BATCH",
  DECISION_DEFINITION = "DECISION_DEFINITION",
  DECISION_REQUIREMENTS_DEFINITION = "DECISION_REQUIREMENTS_DEFINITION",
  RESOURCE = "RESOURCE",
  GROUP = "GROUP",
  MAPPING_RULE = "MAPPING_RULE",
  MESSAGE = "MESSAGE",
  PROCESS_DEFINITION = "PROCESS_DEFINITION",
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

type PermissionOnResources = {
  permissionType: PermissionType;
  resourceIds: string[];
};

export type UserAuthorization = EntityData & {
  ownerKey: number;
  ownerType: string;
  resourceType: string;
  permissions: readonly PermissionOnResources[];
};

export enum PatchAuthorizationAction {
  ADD = "ADD",
  REMOVE = "REMOVE",
}

export type PatchAuthorizationParams = {
  ownerKey: number;
  action: PatchAuthorizationAction;
  resourceType: string;
  permissions: readonly PermissionOnResources[];
};

export const patchAuthorizations: ApiDefinition<
  undefined,
  PatchAuthorizationParams
> = (params) => apiPatch(path(params.ownerKey), params);

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

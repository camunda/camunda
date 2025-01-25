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
  apiPost,
  apiPatch,
  pathBuilder,
  // apiDelete,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export const AUTHORIZATIONS_ENDPOINT = "/authorizations";

const path = pathBuilder("/authorizations");

export enum PermissionType {
  CREATE = "CREATE",
  READ = "READ",
  READ_INSTANCE = "READ_INSTANCE",
  READ_USER_TASK = "READ_USER_TASK",
  UPDATE = "UPDATE",
  DELETE = "DELETE",
  DELETE_PROCESS = "DELETE_PROCESS",
  DELETE_DRD = "DELETE_DRD",
  DELETE_FORM = "DELETE_FORM",
}

export type Permission = {
  permissionType: PermissionType;
  resourceIds: string[];
};

export type Authorization = EntityData & {
  ownerKey: number;
  ownerType: string;
  resourceType: string;
  permissions: readonly Permission[];
};

export type AuthorizationParams = {
  key: string;
  ownerType: string;
  ownerId: string;
  resourceId: string;
  resourceType: string;
  permissions: readonly string[];
};

// @TODO: Remove and consolidate in Authorization type when BE is implemented, change all instances using this type
export type NewAuthorization = EntityData & AuthorizationParams;

export enum PatchAuthorizationAction {
  ADD = "ADD",
  REMOVE = "REMOVE",
}

export type PatchAuthorizationParams = {
  ownerKey: number;
  action: PatchAuthorizationAction;
  resourceType: string;
  permissions: readonly Permission[];
};

type CreateAuthorizationParams = Omit<AuthorizationParams, "key">;

export const searchAuthorization: ApiDefinition<
  SearchResponse<Authorization>
> = () => apiPost(`${AUTHORIZATIONS_ENDPOINT}/search`);

export const patchAuthorizations: ApiDefinition<
  undefined,
  PatchAuthorizationParams
> = (params) => apiPatch(path(params.ownerKey), params);

export const createAuthorization: ApiDefinition<
  undefined,
  CreateAuthorizationParams
> = (authorization) => apiPost(AUTHORIZATIONS_ENDPOINT, authorization);

// export const deleteTenant: ApiDefinition<undefined, { tenantKey: string }> = ({
//   tenantKey,
// }) => apiDelete(`${AUTHORIZATIONS_ENDPOINT}/${tenantKey}`);

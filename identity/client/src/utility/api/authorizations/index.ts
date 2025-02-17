/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { EntityData } from "src/components/entityList/EntityList";
import { ApiDefinition, apiPatch, pathBuilder } from "src/utility/api/request";

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

export const patchAuthorizations: ApiDefinition<
  undefined,
  PatchAuthorizationParams
> = (params) => apiPatch(path(params.ownerKey), params);

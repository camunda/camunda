/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  pathBuilder,
} from "src/utility/api/request.ts";
import { Role } from "src/utility/api/roles";
import { GROUPS_ENDPOINT } from "src/utility/api/groups/index.ts";

export type GetGroupRolesParams = { id: string };

const path = pathBuilder(GROUPS_ENDPOINT);

export const getGroupRoles: ApiDefinition<Role[], GetGroupRolesParams> = ({
  id,
}) => apiGet(path(id, "roles"));

export type AssignGroupRoleParams = GetGroupRolesParams & {
  roleId: Role["id"];
};

export const assignGroupRole: ApiDefinition<
  undefined,
  AssignGroupRoleParams
> = ({ id, roleId }) => apiPost(path(id, "roles"), { roleId: roleId });

export type RemoveGroupRoleParams = GetGroupRolesParams & {
  roleId: Role["id"];
};

export const removeGroupRole: ApiDefinition<
  undefined,
  RemoveGroupRoleParams
> = ({ id, roleId }) => apiDelete(path(id, "roles", roleId));

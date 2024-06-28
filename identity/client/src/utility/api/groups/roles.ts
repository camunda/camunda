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
import {
  AssignRoleParams,
  UnassignRoleParams,
} from "src/utility/api/roles/assign";

export type GetGroupRolesParams = { id: string };

const path = pathBuilder(GROUPS_ENDPOINT);

export const getGroupRoles: ApiDefinition<Role[], GetGroupRolesParams> = ({
  id,
}) => apiGet(path(id, "roles"));

export const assignGroupRole: ApiDefinition<undefined, AssignRoleParams> = ({
  id,
  roleId,
}) => apiPost(path(id, "roles"), { roleId: roleId });

export const removeGroupRole: ApiDefinition<undefined, UnassignRoleParams> = ({
  id,
  roleId,
}) => apiDelete(path(id, "roles", roleId));

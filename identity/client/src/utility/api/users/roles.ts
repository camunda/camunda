/*
 * @license Identity
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license
 * agreements. Licensed under a proprietary license. See the License.txt file for more information. You may not use this
 * file except in compliance with the proprietary license.
 */

import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
} from "src/utility/api/request";
import { USERS_ENDPOINT } from "src/utility/api/users/index";
import { Role } from "src/utility/api/roles";

export type GetUserRolesParams = { id: string };

export const getUserRoles: ApiDefinition<Role[], GetUserRolesParams> = ({
  id,
}) => apiGet(`/v2${USERS_ENDPOINT}/${id}/roles`);

export type AssignUserRoleParams = GetUserRolesParams & {
  roleId: Role["id"];
};

export const assignUserRole: ApiDefinition<undefined, AssignUserRoleParams> = ({
  id,
  roleId,
}) => apiPost(`/v2${USERS_ENDPOINT}/${id}/roles`, { roleId: roleId });

export type RemoveUserRoleParams = GetUserRolesParams & {
  roleId: Role["id"];
};

export const removeUserRole: ApiDefinition<undefined, RemoveUserRoleParams> = ({
  id,
  roleId,
}) => apiDelete(`/v2${USERS_ENDPOINT}/${id}/roles/${roleId}`);

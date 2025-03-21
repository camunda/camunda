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
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export const ROLES_ENDPOINT = "/roles";

export type Role = {
  roleKey: string;
  name: string;
  description: string;
};

export const searchRoles: ApiDefinition<SearchResponse<Role>> = () =>
  apiPost(`${ROLES_ENDPOINT}/search`);

type GetRoleParams = {
  roleKey: string;
};
export const getRoleDetails: ApiDefinition<Role, GetRoleParams> = ({
  roleKey,
}) => apiGet(`${ROLES_ENDPOINT}/${roleKey}`);

type CreateRoleParams = Omit<Role, "roleKey">;
export const createRole: ApiDefinition<Role, CreateRoleParams> = (role) =>
  apiPost(ROLES_ENDPOINT, role);

export type DeleteRoleParams = {
  roleKey: string;
  name: string;
};
export const deleteRole: ApiDefinition<undefined, { roleKey: string }> = ({
  roleKey,
}) => apiDelete(`${ROLES_ENDPOINT}/${roleKey}`);

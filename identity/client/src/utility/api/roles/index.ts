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
  apiPut,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export const ROLES_ENDPOINT = "/roles";

export type Role = {
  name: string;
  description: string;
  permissions: string[];
};

export const searchRoles: ApiDefinition<SearchResponse<Role>> = () =>
  apiPost(`${ROLES_ENDPOINT}/search`);

type GetRoleParams = {
  name: string;
};

export const getRole: ApiDefinition<Role, GetRoleParams> = ({ name }) =>
  apiGet(`${ROLES_ENDPOINT}/${name}`);

type CreateRoleParams = Role;

export const createRole: ApiDefinition<Role, CreateRoleParams> = (role) =>
  apiPost(ROLES_ENDPOINT, role);

type UpdateRoleParams = Role;

export const updateRole: ApiDefinition<Role, UpdateRoleParams> = ({
  name,
  ...role
}) => apiPut(`${ROLES_ENDPOINT}/${name}`, role);

type DeleteRoleParams = GetRoleParams;

export const deleteRole: ApiDefinition<undefined, DeleteRoleParams> = ({
  name,
}) => apiDelete(`${ROLES_ENDPOINT}/${name}`);

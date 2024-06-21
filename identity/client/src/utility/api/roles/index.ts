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
  pathBuilder,
} from "src/utility/api/request";

const path = pathBuilder("/v2/roles");

export type Role = {
  id: string;
  name: string;
  description: string;
  permissions: string[];
};

export const getRoles: ApiDefinition<Role[]> = () => apiGet(path());

type GetRoleParams = {
  id: string;
};

export const getRole: ApiDefinition<Role, GetRoleParams> = ({ id }) =>
  apiGet(path(id));

type CreateRoleParams = Omit<Role, "id">;

export const createRole: ApiDefinition<Role, CreateRoleParams> = (role) =>
  apiPost(path(), role);

type UpdateRoleParams = Role;

export const updateRole: ApiDefinition<Role, UpdateRoleParams> = ({
  id,
  ...role
}) => apiPut(path(id), role);

type DeleteRoleParams = GetRoleParams;

export const deleteRole: ApiDefinition<undefined, DeleteRoleParams> = ({
  id,
}) => apiDelete(path(id));

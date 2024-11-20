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
  apiPatch,
  apiPost,
  pathBuilder,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

const path = pathBuilder("/roles");

export type Role = {
  key: number;
  name: string;
  description: string;
  permissions: string[];
};

export const searchRoles: ApiDefinition<SearchResponse<Role>> = () =>
  apiPost(path("search"), {});

type GetRoleParams = {
  key: number;
};

export const getRole: ApiDefinition<Role, GetRoleParams> = ({ key }) =>
  apiGet(path(key));

type CreateRoleParams = Omit<Role, "key">;

export const createRole: ApiDefinition<Role, CreateRoleParams> = (role) =>
  apiPost(path(), role);

type UpdateRoleParams = Role;

export const updateRole: ApiDefinition<Role, UpdateRoleParams> = ({
  key,
  ...role
}) => apiPatch(path(key), { changeset: role });

interface DeleteRoleParams {
  key: number;
}

export const deleteRole: ApiDefinition<undefined, DeleteRoleParams> = ({
  key,
}) => apiDelete(path(key));

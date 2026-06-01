/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { mutationOptions, QueryClient } from "@tanstack/react-query";
import type {
  Group,
  MappingRule,
  Role,
  TenantClient,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  assignGroupClient,
  assignGroupMappingRule,
  assignGroupRole,
  createGroup,
  deleteGroup,
  unassignGroupClient,
  unassignGroupMappingRule,
  unassignGroupRole,
  updateGroup,
} from ".";

export const groupMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Group) => unwrap(createGroup(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups.all }),
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Group) => unwrap(updateGroup(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups.all }),
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId">) =>
        unwrap(deleteGroup(body)(getApiBaseUrl())),
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.groups.all }),
    }),
  assignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<Role, "roleId">) =>
        unwrap(assignGroupRole(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "roles", variables.groupId],
        }),
    }),
  unassignRole: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<Role, "roleId">) =>
        unwrap(unassignGroupRole(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "roles", variables.groupId],
        }),
    }),
  assignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(assignGroupMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "mappingRules", variables.groupId],
        }),
    }),
  unassignMappingRule: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">,
      ) => unwrap(unassignGroupMappingRule(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "mappingRules", variables.groupId],
        }),
    }),
  assignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(assignGroupClient(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "clients", variables.groupId],
        }),
    }),
  unassignClient: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (
        body: Pick<Group, "groupId"> & Pick<TenantClient, "clientId">,
      ) => unwrap(unassignGroupClient(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "clients", variables.groupId],
        }),
    }),
};

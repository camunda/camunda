/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AuthorizationResult,
  PermissionType,
  OwnerType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.9";
import {
  resourceTypeSchema,
  ownerTypeSchema,
} from "@camunda/camunda-api-zod-schemas/8.9";
import { ApiDefinition, apiDelete, apiPost } from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export const AUTHORIZATIONS_ENDPOINT = "/authorizations";

export { PermissionType, OwnerType, ResourceType };

export const ALL_RESOURCE_TYPES: ResourceType[] = [
  ...resourceTypeSchema.options,
];

export const RESOURCE_TYPES_WITHOUT_TENANT: ResourceType[] =
  ALL_RESOURCE_TYPES.filter((type) => type !== "TENANT");

export const OWNER_TYPES: OwnerType[] = [...ownerTypeSchema.options];

export type ResourcePropertyName =
  | "assignee"
  | "candidateGroups"
  | "candidateUsers"
  | null;

export const RESOURCE_PROPERTY_NAMES: ResourcePropertyName[] = [
  "assignee",
  "candidateGroups",
  "candidateUsers",
];

export type Authorization = AuthorizationResult;

export type NewAuthorization = Omit<
  Authorization,
  "authorizationKey" | "resourcePropertyName"
> & {
  resourcePropertyName: ResourcePropertyName | null;
};

export enum PatchAuthorizationAction {
  ADD = "ADD",
  REMOVE = "REMOVE",
}

export type searchAuthorizationsParams = {
  filter: {
    resourceType: ResourceType;
  };
};

export const searchAuthorization: ApiDefinition<
  SearchResponse<Authorization>,
  searchAuthorizationsParams
> = (param) => apiPost(`${AUTHORIZATIONS_ENDPOINT}/search`, param);

export const createAuthorization: ApiDefinition<undefined, NewAuthorization> = (
  authorization,
) => apiPost(AUTHORIZATIONS_ENDPOINT, authorization);

export type DeleteAuthorizationParams = {
  authorizationKey: string;
};

export const deleteAuthorization: ApiDefinition<
  undefined,
  DeleteAuthorizationParams
> = ({ authorizationKey }) =>
  apiDelete(`${AUTHORIZATIONS_ENDPOINT}/${authorizationKey}`);

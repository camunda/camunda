/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  Authorization,
  OwnerType,
  ResourceType,
  QueryAuthorizationsRequestBody,
  QueryAuthorizationsResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import {
  resourceTypeSchema,
  ownerTypeSchema,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, apiDelete, apiPost } from "src/utility/api/request";

export const AUTHORIZATIONS_ENDPOINT = "/authorizations";

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

export type NewAuthorization = Omit<
  Authorization,
  "authorizationKey" | "resourcePropertyName"
> & {
  resourcePropertyName: ResourcePropertyName | null;
};

export const searchAuthorization: ApiDefinition<
  QueryAuthorizationsResponseBody,
  QueryAuthorizationsRequestBody
> = (param) => apiPost(`${AUTHORIZATIONS_ENDPOINT}/search`, param);

export const createAuthorization: ApiDefinition<undefined, NewAuthorization> = (
  authorization,
) => apiPost(AUTHORIZATIONS_ENDPOINT, authorization);

export const deleteAuthorization: ApiDefinition<
  undefined,
  Pick<Authorization, "authorizationKey">
> = ({ authorizationKey }) =>
  apiDelete(`${AUTHORIZATIONS_ENDPOINT}/${authorizationKey}`);

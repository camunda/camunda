/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  MappingRule,
  QueryMappingRulesRequestBody,
  QueryMappingRulesResponseBody,
  UpdateMappingRuleRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import {
  ApiDefinition,
  apiDelete,
  apiPut,
  apiPost,
} from "src/utility/api/request";

export type MappingRuleType =
  "TOKEN_CLAIM" | "USER_ATTRIBUTE" | "GROUP_ATTRIBUTE";

/**
 * A mapping rule either matches a claim of the login token (the default) or
 * an attribute provisioned via SCIM. `type` is not part of the published API
 * schema yet.
 */
export type TypedMappingRule = MappingRule & { type?: MappingRuleType };

export type QueryTypedMappingRulesResponseBody = Omit<
  QueryMappingRulesResponseBody,
  "items"
> & { items: TypedMappingRule[] };

export const MAPPING_RULES_ENDPOINT = "/mapping-rules";

export const searchMappingRule: ApiDefinition<
  QueryTypedMappingRulesResponseBody,
  QueryMappingRulesRequestBody | undefined
> = (params) => apiPost(`${MAPPING_RULES_ENDPOINT}/search`, params);

export const createMappingRule: ApiDefinition<undefined, TypedMappingRule> = (
  mappingRule,
) => apiPost(MAPPING_RULES_ENDPOINT, mappingRule);

export const updateMappingRule: ApiDefinition<
  undefined,
  UpdateMappingRuleRequestBody &
    Pick<MappingRule, "mappingRuleId"> & { type: MappingRuleType }
> = ({ mappingRuleId, claimName, claimValue, name, type }) =>
  // The backend resets an omitted type to TOKEN_CLAIM, so it is always sent.
  apiPut(`${MAPPING_RULES_ENDPOINT}/${encodeURIComponent(mappingRuleId)}`, {
    name,
    claimName,
    claimValue,
    type,
  });

export const deleteMappingRule: ApiDefinition<
  undefined,
  Pick<MappingRule, "mappingRuleId">
> = ({ mappingRuleId }) =>
  apiDelete(`${MAPPING_RULES_ENDPOINT}/${encodeURIComponent(mappingRuleId)}`);

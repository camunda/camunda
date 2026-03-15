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
} from "@camunda/camunda-api-zod-schemas/8.9";
import {
  ApiDefinition,
  apiDelete,
  apiPut,
  apiPost,
} from "src/utility/api/request";

export const MAPPING_RULES_ENDPOINT = "/mapping-rules";

export const searchMappingRule: ApiDefinition<
  QueryMappingRulesResponseBody,
  QueryMappingRulesRequestBody | undefined
> = (params) => apiPost(`${MAPPING_RULES_ENDPOINT}/search`, params);

export const createMappingRule: ApiDefinition<undefined, MappingRule> = (
  mappingRule,
) => apiPost(MAPPING_RULES_ENDPOINT, mappingRule);

export const updateMappingRule: ApiDefinition<
  undefined,
  UpdateMappingRuleRequestBody & Pick<MappingRule, "mappingRuleId">
> = ({ mappingRuleId, claimName, claimValue, name }) =>
  apiPut(`${MAPPING_RULES_ENDPOINT}/${encodeURIComponent(mappingRuleId)}`, {
    name,
    claimName,
    claimValue,
  });

export const deleteMappingRule: ApiDefinition<
  undefined,
  Pick<MappingRule, "mappingRuleId">
> = ({ mappingRuleId }) =>
  apiDelete(`${MAPPING_RULES_ENDPOINT}/${encodeURIComponent(mappingRuleId)}`);

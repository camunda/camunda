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
  apiPut,
  apiPost,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export const MAPPING_RULES_ENDPOINT = "/mapping-rules";

export type MappingRule = {
  mappingRuleId: string;
  name: string;
  claimName: string;
  claimValue: string;
};

type MappingRuleSearchParams = {
  filter?: {
    name?: string;
    claimName?: string;
    claimValue?: string;
    mappingRuleId?: string;
  };
};

export const searchMappingRule: ApiDefinition<
  SearchResponse<MappingRule>,
  MappingRuleSearchParams | undefined
> = (params) => apiPost(`${MAPPING_RULES_ENDPOINT}/search`, params);

export const createMappingRule: ApiDefinition<undefined, MappingRule> = (
  mappingRule,
) => apiPost(MAPPING_RULES_ENDPOINT, mappingRule);

export type UpdateMappingRuleParams = {
  mappingRuleId: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const updateMappingRule: ApiDefinition<
  undefined,
  UpdateMappingRuleParams
> = ({ mappingRuleId, claimName, claimValue, name }) =>
  apiPut(`${MAPPING_RULES_ENDPOINT}/${mappingRuleId}`, {
    name,
    claimName,
    claimValue,
  });

export type DeleteMappingRuleParams = UpdateMappingRuleParams;
export const deleteMappingRule: ApiDefinition<
  undefined,
  { mappingRuleId: string }
> = ({ mappingRuleId }) =>
  apiDelete(`${MAPPING_RULES_ENDPOINT}/${mappingRuleId}`);

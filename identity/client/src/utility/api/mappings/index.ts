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

export const MAPPINGS_ENDPOINT = "/mapping-rules";

export type Mapping = {
  mappingRuleId: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const searchMapping: ApiDefinition<SearchResponse<Mapping>> = () =>
  apiPost(`${MAPPINGS_ENDPOINT}/search`);

export const createMapping: ApiDefinition<undefined, Mapping> = (mapping) =>
  apiPost(MAPPINGS_ENDPOINT, mapping);

export type UpdateMappingParams = {
  mappingRuleId: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const updateMapping: ApiDefinition<undefined, UpdateMappingParams> = ({
  mappingRuleId,
  claimName,
  claimValue,
  name,
}) =>
  apiPut(`${MAPPINGS_ENDPOINT}/${mappingRuleId}`, {
    name,
    claimName,
    claimValue,
  });

export type DeleteMappingParams = UpdateMappingParams;
export const deleteMapping: ApiDefinition<undefined, { mappingRuleId: string }> = ({
  mappingRuleId,
}) => apiDelete(`${MAPPINGS_ENDPOINT}/${mappingRuleId}`);

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
  mappingId: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const searchMapping: ApiDefinition<SearchResponse<Mapping>> = () =>
  apiPost(`${MAPPINGS_ENDPOINT}/search`);

export const createMapping: ApiDefinition<undefined, Mapping> = (mapping) =>
  apiPost(MAPPINGS_ENDPOINT, mapping);

export type UpdateMappingParams = {
  mappingId: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const updateMapping: ApiDefinition<undefined, UpdateMappingParams> = ({
  mappingId,
  claimName,
  claimValue,
  name,
}) =>
  apiPut(`${MAPPINGS_ENDPOINT}/${mappingId}`, {
    name,
    claimName,
    claimValue,
  });

export type DeleteMappingParams = UpdateMappingParams;
export const deleteMapping: ApiDefinition<undefined, { mappingId: string }> = ({
  mappingId,
}) => apiDelete(`${MAPPINGS_ENDPOINT}/${mappingId}`);

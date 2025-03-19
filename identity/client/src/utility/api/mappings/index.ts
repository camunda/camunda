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
  apiPatch,
  apiPost,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { EntityData } from "src/components/entityList/EntityList";

export const MAPPINGS_ENDPOINT = "/mapping-rules";

export type Mapping = EntityData & {
  mappingKey: string;
  id: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const searchMapping: ApiDefinition<SearchResponse<Mapping>> = () =>
  apiPost(`${MAPPINGS_ENDPOINT}/search`);

type CreateMappingParams = Omit<Mapping, "mappingKey">;

export const createMapping: ApiDefinition<undefined, CreateMappingParams> = (
  mapping,
) => apiPost(MAPPINGS_ENDPOINT, mapping);

export type UpdateMappingParams = {
  mappingKey: string;
  id: string;
  name: string;
  claimName: string;
  claimValue: string;
};

export const updateMapping: ApiDefinition<undefined, UpdateMappingParams> = ({
  mappingKey,
  claimName,
  claimValue,
  name,
}) =>
  apiPatch(`${MAPPINGS_ENDPOINT}/${mappingKey}`, {
    name,
    claimName,
    claimValue,
  });

export type DeleteMappingParams = UpdateMappingParams;
export const deleteMapping: ApiDefinition<
  undefined,
  { mappingKey: string }
> = ({ mappingKey }) => apiDelete(`${MAPPINGS_ENDPOINT}/${mappingKey}`);

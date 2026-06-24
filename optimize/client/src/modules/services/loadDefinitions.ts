/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';

export type Definition = {
  key: string;
  name: string | null;
};

export async function loadDefinitions(
  type: string,
  collectionId: string | null,
  options?: {onlyWithAgentRuns?: boolean}
): Promise<Definition[]> {
  const params: {filterByCollectionScope?: string; onlyWithAgentRuns?: boolean} = {};

  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  if (options?.onlyWithAgentRuns) {
    params.onlyWithAgentRuns = true;
  }

  const response = await get(`api/definition/${type}/keys`, params);

  return response.json();
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Tenant} from 'types';
import {get, post} from 'request';

export type Definition = {
  key: string;
  name: string | null;
};

export type Version = {
  version: string;
  versionTag: string | null;
};

type DefintionWithTenants = {
  key: string;
  versions: string[];
  tenants: Tenant[];
};

type DefintionWithVersions = {key: string; versions: string[]};

export async function loadDefinitions(
  type: string,
  collectionId: string | null,
  camundaEventImportedOnly = false
): Promise<Definition[]> {
  const params: {camundaEventImportedOnly: boolean; filterByCollectionScope?: string} = {
    camundaEventImportedOnly,
  };

  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await get(`api/definition/${type}/keys`, params);

  return response.json();
}

export async function loadVersions(
  type: string,
  collectionId: string | null,
  key: string
): Promise<Version[]> {
  const params: {filterByCollectionScope?: string} = {};
  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await get(`api/definition/${type}/${key}/versions`, params);

  return response.json();
}

export async function loadTenants(
  type: string,
  definitions: DefintionWithVersions[],
  collectionId?: string | null
): Promise<DefintionWithTenants[]> {
  const payload: {filterByCollectionScope?: string; definitions: DefintionWithVersions[]} = {
    definitions,
  };
  if (collectionId) {
    payload.filterByCollectionScope = collectionId;
  }

  const response = await post(`api/definition/${type}/_resolveTenantsForVersions`, payload);

  return response.json();
}

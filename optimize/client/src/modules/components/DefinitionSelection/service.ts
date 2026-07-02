/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tenant} from 'types';
import {post} from 'request';
import {loadVersions} from 'services';
import type {Version} from 'services';

export {loadVersions};
export type {Version};

type DefintionWithTenants = {
  key: string;
  versions: string[];
  tenants: Tenant[];
};

type DefintionWithVersions = {key: string; versions: string[]};

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

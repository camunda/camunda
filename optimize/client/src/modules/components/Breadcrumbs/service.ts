/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';

export type EntityNamesResponse = {
  reportName: string | null;
  dashboardName: string | null;
  collectionName: string | null;
};

export async function loadEntitiesNames(entitiesIds: {
  reportId?: string | null;
  dashboardId?: string | null;
  collectionId?: string | null;
}): Promise<EntityNamesResponse> {
  const res = await get('api/entities/names', entitiesIds);

  return await res.json();
}

export function getEntityId(
  type: 'collection' | 'report' | 'dashboard',
  path: string
): string | null {
  const entityMatchMatch = new RegExp(`/${type}/([^/]+)`, 'g').exec(path);
  const entityId = entityMatchMatch?.[1];

  if (!entityId || entityId === 'new') {
    return null;
  }

  return entityId;
}

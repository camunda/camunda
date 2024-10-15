/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EntityListEntity, Source} from 'types';
import {get, put} from 'request';

export async function addSources(collectionId: string, sources: Source[]) {
  return await put(`api/collection/${collectionId}/scope`, sources);
}

export function getCollection(path: string) {
  const collectionMatch = /\/collection\/([^/]+)/g.exec(path);
  return collectionMatch && collectionMatch[1];
}

export async function loadCollectionEntities<T extends Record<string, unknown>>(
  id: string,
  sortBy?: string,
  sortOrder?: string
): Promise<EntityListEntity<T>[]> {
  const params: Record<string, unknown> = {};
  if (sortBy && sortOrder) {
    params.sortBy = sortBy;
    params.sortOrder = sortOrder;
  }

  const response = await get(`api/collection/${id}/entities`, params);
  return await response.json();
}
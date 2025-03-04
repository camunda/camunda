/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Source} from 'types';
import {put} from 'request';

export async function addSources(collectionId: string, sources: Source[]) {
  return await put(`api/collection/${collectionId}/scope`, sources);
}

export function getCollection(path: string) {
  const collectionMatch = /\/collection\/([^/]+)/g.exec(path);
  return collectionMatch && collectionMatch[1];
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

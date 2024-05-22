/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';

export type Definition = {
  key: string;
  name: string | null;
};

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

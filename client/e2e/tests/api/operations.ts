/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetch from 'node-fetch';
import {ENDPOINTS} from './endpoints';
import {getCredentials} from './getCredentials';

async function createOperation({
  id,
  operationType,
}: {
  id: string;
  operationType: 'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE';
}) {
  const credentials = await getCredentials();

  return await fetch(ENDPOINTS.createOperation(id), {
    method: 'POST',
    body: JSON.stringify({operationType}),
    headers: {
      'Content-Type': 'application/json',
      ...credentials,
    },
  }).then((response) => response.json());
}

export {createOperation};

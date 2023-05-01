/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';
import {GenericReport} from 'types';

export async function loadReports(collection?: string | null): Promise<GenericReport[]> {
  let url = 'api/report';
  if (collection) {
    url = `api/collection/${collection}/reports`;
  }
  const response = await get(url);
  return await response.json();
}

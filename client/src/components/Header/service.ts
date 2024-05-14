/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';

export async function isEventBasedProcessEnabled(): Promise<boolean> {
  const response = await get('api/eventBasedProcess/isEnabled');
  const value = await response.text();

  return value === 'true';
}

export async function getUserToken(): Promise<string> {
  const response = await get('api/token');
  const value = (await response.json()) as {token: string};
  return value.token;
}

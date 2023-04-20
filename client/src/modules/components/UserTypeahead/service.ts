/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';

export interface User {
  id: string;
  identity: {
    name: string;
    email?: string;
    id: string;
    type: string;
    memberCount?: string;
  };
}

export async function searchIdentities(
  terms: string,
  excludeUserGroups: boolean
): Promise<{total: number; result: User['identity'][]}> {
  const response = await get('api/identity/search', {terms, excludeUserGroups});
  return await response.json();
}

export async function getUser(id: string): Promise<User['identity']> {
  const response = await get(`api/identity/${id}`);
  return await response.json();
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';

export interface User {
  id: string;
  identity: {
    name: string;
    email?: string;
    id: string | null;
  };
  type?: string;
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

export function getUserId(id: string | null): string {
  return `USER:${id}`;
}

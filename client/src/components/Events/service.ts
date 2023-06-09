/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post, put} from 'request';

import {User} from 'components';

export async function getUsers(id: string): Promise<User[]> {
  const response = await get(`api/eventBasedProcess/${id}/role`);
  return await response.json();
}

export async function updateUsers(id: string, newUsers: User[]) {
  return await put(`api/eventBasedProcess/${id}/role`, newUsers);
}

export async function publish(id: string) {
  return await post(`api/eventBasedProcess/${id}/_publish`);
}

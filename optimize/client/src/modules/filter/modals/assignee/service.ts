/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {User} from 'components';
import {post, get} from 'request';
import {Definition} from 'types';

export async function loadUsersByDefinition(
  type: string,
  payload: {
    processDefinitionKey: Definition['key'];
    tenantIds: Definition['tenantIds'];
    terms: string;
  }
) {
  const response = await post(`api/${type}/search`, payload);

  return await response.json();
}

export async function loadUsersByReportIds(
  type: string,
  payload: {
    reportIds: string[];
    terms: string;
  }
) {
  const response = await post(`api/${type}/search/reports`, payload);

  return await response.json();
}

export async function getUsersById(type: string, ids: (string | null)[]): Promise<User[]> {
  const response = await get(`api/${type}`, {idIn: ids.join(',')});

  return await response.json();
}

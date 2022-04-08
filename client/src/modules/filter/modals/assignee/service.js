/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post, get} from 'request';

export async function loadUsersByDefinition(type, payload) {
  const response = await post(`api/${type}/search`, payload);

  return await response.json();
}

export async function loadUsersByReportIds(type, payload) {
  const response = await post(`api/${type}/search/reports`, payload);

  return await response.json();
}

export async function getUsersById(type, ids) {
  const response = await get(`api/${type}`, {idIn: ids.join(',')});

  return await response.json();
}

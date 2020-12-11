/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, get} from 'request';

export async function loadUsers(type, payload) {
  const response = await post(`api/${type}/search`, payload);

  return await response.json();
}

export async function getUsersById(type, ids) {
  const response = await get(`api/${type}`, {idIn: ids.join(',')});

  return await response.json();
}

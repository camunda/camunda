/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export async function login(username, password) {
  try {
    const response = await post('api/authentication', {username, password});
    const token = await response.text();

    return {token};
  } catch (e) {
    return await e.json();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'modules/request';

function logout() {
  return post('/api/logout');
}

async function fetchUser() {
  const response = await get('/api/authentications/user');
  return response.json();
}

export {logout, fetchUser};

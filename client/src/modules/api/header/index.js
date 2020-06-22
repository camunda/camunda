/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'modules/request';

export const logout = async () => {
  await post('/api/logout');
};

export const fetchUser = async () => {
  const response = await get('/api/authentications/user');
  return await response.json();
};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'modules/request';

const logoutUrl = '/api/logout';
function logout() {
  // @ts-expect-error ts-migrate(2554) FIXME: Expected 2-3 arguments, but got 1.
  return post(logoutUrl);
}

async function fetchUser() {
  const response = await get('/api/authentications/user');
  return response.json();
}

export {logout, fetchUser, logoutUrl};

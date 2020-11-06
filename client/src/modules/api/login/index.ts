/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

type Credentials = {
  username: string;
  password: string;
};

async function login({username, password}: Credentials) {
  const body = new URLSearchParams([
    ['username', username],
    ['password', password],
  ]).toString();

  return post('/api/login', body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
}

export {login};

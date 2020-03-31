/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

export const login = async ({username, password}) => {
  const body = `username=${username}&password=${password}`;
  await post('/api/login', body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
};

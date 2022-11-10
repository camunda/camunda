/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type Credentials = {
  username: string;
  password: string;
};

const login = async ({username, password}: Credentials) => {
  const body = new URLSearchParams([
    ['username', username],
    ['password', password],
  ]).toString();

  return requestAndParse({
    url: '/api/login',
    method: 'POST',
    body,
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
};

export {login};
export type {Credentials};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import config from './config';

let users = [];

export async function loadUsers() {
  if (!users.length) {
    users = (await getUsersFromRealm()).filter((user) => user.username !== 'demo');
  }

  const browser1 = createMapOfUsers(users.slice(0, 2));
  const browser2 = createMapOfUsers(users.slice(2, 4));
  const borwser3 = createMapOfUsers(users.slice(4, 6));
  const browsers = [browser1, browser2, borwser3];

  return {
    Chrome: browsers,
    headless: browsers,
    Firefox: browsers,
    'Microsoft Edge': browsers,
  };
}

async function getUsersFromRealm() {
  const {endpoint, username, password, client_id} = config.keycloak;
  try {
    // Get access token
    const tokenResponse = await fetch(`${endpoint}/realms/master/protocol/openid-connect/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'password',
        username,
        password,
        client_id,
      }),
    });
    const tokenData = await tokenResponse.json();
    const accessToken = tokenData.access_token;

    // Get users from the specified realm
    const usersResponse = await fetch(`${endpoint}/admin/realms/camunda-platform/users`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });
    const usersData = await usersResponse.json();

    return usersData;
  } catch (error) {
    console.error('Error fetching users:', error);
    throw error;
  }
}

function createMapOfUsers(users) {
  return users.reduce((map, user, idx) => {
    map['user' + (idx + 1)] = user;
    return map;
  }, {});
}

export default users;

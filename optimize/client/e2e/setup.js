/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetch from 'node-fetch';

import config from './config';

export async function cleanEntities({ctx}) {
  const indicesToDelete = [
    'optimize-single-process-report',
    'optimize-report-share',
    'optimize-dashboard-share',
    'optimize-collection',
    'optimize-alert',
    'optimize-dashboard',
  ];

  let users = ctx.users.map((user) => user.username);

  if (process.env.CONTEXT === 'sm') {
    users = await getUsersIds(ctx.users);
  } else {
    indicesToDelete.push('optimize-combined-report', 'optimize-single-decision-report');
  }

  deleteIndicesContent(indicesToDelete, users);
}

async function deleteIndicesContent(indicesToDelete, users) {
  try {
    for (const index of indicesToDelete) {
      const response = await fetch(`${config.elasticSearchEndpoint}/${index}/_delete_by_query`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          query: {
            bool: {
              must_not: [
                {
                  term: {
                    managementDashboard: 'true',
                  },
                },
                {
                  term: {
                    'data.managementReport': 'true',
                  },
                },
              ],
              must: [
                {
                  terms: {
                    owner: users,
                  },
                },
              ],
            },
          },
        }),
      });

      if (!response.ok) {
        console.error(`Failed to delete content of index ${index}. Status: ${response.status}`);
      }
    }
  } catch (error) {
    console.error('Error occurred:', error);
  }
}

export async function cleanEventProcesses() {
  const headers = {
    Cookie: `X-Optimize-Authorization="Bearer ${await getSession(config.users.Chrome[0].user1)}"`,
    'Content-Type': 'application/json',
  };

  const response = await fetch(`${config.endpoint}/api/eventBasedProcess`, {headers});
  const processes = await response.json();
  const processesIds = processes.map(({id}) => id);

  await fetch(`${config.endpoint}/api/eventBasedProcess/delete`, {
    method: 'POST',
    headers,
    body: JSON.stringify(processesIds),
  });
}

async function getSession(user) {
  const resp = await fetch(config.endpoint + '/api/authentication', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(user),
  });
  return await resp.text();
}

async function getUsersIds(configUsers) {
  const keycloakUsers = await getUsersFromRealm();

  return keycloakUsers
    .filter((user) =>
      configUsers.some(
        (configUser) => configUser.username.toLowerCase() === user.username.toLowerCase()
      )
    )
    .map((user) => user.id);
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

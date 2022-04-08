/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetch from 'node-fetch';

import config from './config';

async function getSession(user) {
  const resp = await fetch(config.endpoint + '/api/authentication', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(user),
  });
  return await resp.text();
}

export async function cleanEntities({ctx}) {
  if (ctx.users) {
    for (let i = 0; i < ctx.users.length; i++) {
      const headers = {
        Cookie: `X-Optimize-Authorization="Bearer ${await getSession(ctx.users[i])}"`,
      };

      const response = await fetch(`${config.endpoint}/api/entities`, {headers});
      const entities = await response.json();
      for (let i = 0; i < entities.length; i++) {
        await fetch(
          `${config.endpoint}/api/${entities[i].entityType}/${entities[i].id}?force=true`,
          {
            method: 'DELETE',
            headers,
          }
        );
      }
    }
  }
}

export async function cleanEventProcesses() {
  const headers = {
    Cookie: `X-Optimize-Authorization="Bearer ${await getSession(config.users.Chrome[0].user1)}"`,
  };

  const response = await fetch(`${config.endpoint}/api/eventBasedProcess`, {headers});
  const processes = await response.json();

  for (let i = 0; i < processes.length; i++) {
    await fetch(`${config.endpoint}/api/eventBasedProcess/${processes[i].id}`, {
      method: 'DELETE',
      headers,
    });
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import fetch from 'node-fetch';
import dedent from 'dedent';

import config from './config';
import license from './license';

export async function ensureLicense() {
  const resp = await fetch(config.endpoint + '/api/license/validate');
  if (!resp.ok) {
    await fetch(config.endpoint + '/api/license/validate-and-store', {
      method: 'POST',
      body: license
    });
  }
}

export async function ensureWhatsNewSeenIsSetToSeenForAllUsers() {
  const countResponse = await fetch(
    config.elasticSearchEndpoint + '/optimize-onboarding-state/_doc/_count'
  );
  const countJsonResponse = await countResponse.json();
  if (countJsonResponse.count === 0) {
    const userIndexRequests = [];
    Object.values(config.users).forEach(usersPerBrowser =>
      usersPerBrowser.forEach(userSet =>
        Object.values(userSet).forEach(({username}) => {
          userIndexRequests.push(
            dedent(
              `{ "index" : { "_index" : "optimize-onboarding-state", "_id" : "${username}:whatsnew" } }
            { "id" : "${username}:whatsnew", "userId": "${username}", "key": "whatsnew", "seen": true }`
            )
          );
        })
      )
    );
    // there needs to be a final new line to finish the batch body
    const batchBody = userIndexRequests.join('\n') + '\n';
    const batchResponse = await fetch(
      config.elasticSearchEndpoint + '/optimize-onboarding-state/_doc/_bulk',
      {
        method: 'POST',
        headers: {'Content-Type': 'application/x-ndjson'},
        body: batchBody
      }
    );
    if (!batchResponse.ok) {
      console.error('Failed to set whatsnew seen state for test users.');
      console.error(await batchResponse.text());
    }
  }
}

export async function beforeAllTests() {
  await ensureWhatsNewSeenIsSetToSeenForAllUsers();
  await ensureLicense();
}

async function getSession(user) {
  const resp = await fetch(config.endpoint + '/api/authentication', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(user)
  });
  return await resp.text();
}

export async function cleanEntities({ctx}) {
  if (ctx.users) {
    for (let i = 0; i < ctx.users.length; i++) {
      const headers = {
        Cookie: `X-Optimize-Authorization="Bearer ${await getSession(ctx.users[i])}"`
      };

      const response = await fetch(`${config.endpoint}/api/entities`, {headers});
      const entities = await response.json();

      for (let i = 0; i < entities.length; i++) {
        await fetch(
          `${config.endpoint}/api/${entities[i].entityType}/${entities[i].id}?force=true`,
          {
            method: 'DELETE',
            headers
          }
        );
      }
    }
  }
}

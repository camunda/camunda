/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const fetch = require('node-fetch');
const users = require('../demo-data/users.json');
const url = 'http://localhost:8090/api';

(async () => {
  for (let i = 0; i < users.length; i++) {
    const token = await authenticate(users[i]);
    await setSeen(token, users[i]);
  }
  console.debug('Setting whatsnew seen state for test users finished successfully.');
})();

function authenticate(username) {
  return new Promise(async (resolve) => {
    try {
      const response = await fetch(`${url}/authentication`, {
        method: 'POST',
        headers: {'content-type': 'application/json'},
        body: JSON.stringify({username, password: username}),
      });

      if (response && response.status != 200) {
        console.error('Failed to login for user: ', username);
        console.error(response);
        process.exit(1);
      } else {
        const body = await response.text();
        resolve(body);
      }
    } catch (e) {
      console.error(e);
      process.exit(1);
    }
  });
}

function setSeen(token, username) {
  return new Promise(async (resolve) => {
    try {
      const response = await fetch(`${url}/onboarding/whatsnew`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Cookie: `X-Optimize-Authorization=Bearer ${token};`,
        },
        body: JSON.stringify({seen: true}),
      });

      if (response && response.status != 204) {
        console.error('Failed to set whatsnew seen state for user: ', username);
        console.error(response);
        process.exit(1);
      } else {
        resolve();
      }
    } catch (e) {
      console.error(e);
      process.exit(1);
    }
  });
}

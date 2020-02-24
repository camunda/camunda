/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const request = require('request');
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
  return new Promise(resolve =>
    request.post(
      {
        url: `${url}/authentication`,
        headers: {'content-type': 'application/json'},
        body: {username, password: username},
        json: true
      },
      (err, res, body) => {
        if (err) {
          console.error(err);
          process.exit(1);
        } else if (res && res.statusCode !== 200) {
          console.error('Failed to login for user: ', username);
          console.error(body);
          process.exit(1);
        } else {
          resolve(body);
        }
      }
    )
  );
}

function setSeen(token, username) {
  return new Promise(resolve =>
    request.put(
      {
        url: `${url}/onboarding/whatsnew`,
        headers: {
          'Content-Type': 'application/json',
          Cookie: `X-Optimize-Authorization=Bearer ${token};`
        },
        body: {seen: true},
        json: true
      },
      function(error, response, body) {
        if (error) {
          console.error(error);
          process.exit(1);
        } else if (response && response.statusCode !== 204) {
          console.error('Failed to set whatsnew seen state for user: ', username);
          console.error(response.statusCode);
          console.error(body);
          process.exit(1);
        } else {
          resolve();
        }
      }
    )
  );
}

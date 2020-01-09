/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const request = require('request');
const dedent = require('dedent');
const users = require('../demo-data/users.json');

console.debug('Setting whatsnew seen state.');

const userIndexRequests = [];
Object.values(users).forEach(username =>
  userIndexRequests.push(
    dedent(
      `{ "index" : { "_index" : "optimize-onboarding-state", "_id" : "${username}:whatsnew" } }
       { "id" : "${username}:whatsnew", "userId": "${username}", "key": "whatsnew", "seen": true }`
    )
  )
);
// there needs to be a final new line to finish the batch body
const batchBody = userIndexRequests.join('\n') + '\n';

request.post(
  {
    url: 'http://localhost:9200/optimize-onboarding-state/_doc/_bulk',
    headers: {
      'Content-Type': 'application/x-ndjson'
    },
    body: batchBody
  },
  function(error, response, body){
    if (error) {
      console.error(error);
      process.exit(1);
    } else if (response && response.statusCode != 200) {
      console.error('Failed to set whatsnew seen state for test users.');
      console.error(body);
      process.exit(1);
    } else {
      console.debug("Setting whatsnew seen state for test users finished successfully.");
      process.exit(0);
    }
  }
);

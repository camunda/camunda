/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const request = require('request');
const eventData = require('../demo-data/eventIngestionBatch.json');

console.debug('ingesting event data');

// insert event ingestion sample data
request.post(
  {
    url: 'http://localhost:8090/api/ingestion/event/batch',
    headers: {
        'Authorization': 'secret',
        'Content-Type': 'application/cloudevents-batch+json'
    },
    body: eventData,
    json: true
  },
  function(error, response, body){
    if (error) {
      console.error(error);
      process.exit(1);
    } else if (response && response.statusCode != 204) {
      console.error(body);
      process.exit(1);
    } else {
      console.debug("ingestion of event data finished successfully");
      process.exit(0);
    }
  }
);

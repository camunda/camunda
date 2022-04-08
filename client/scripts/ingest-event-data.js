/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const fetch = require('node-fetch');
const eventData = require('../demo-data/eventIngestionBatch.json');

console.debug('ingesting event data');

// insert event ingestion sample data
(async () => {
  try {
    const response = await fetch('http://localhost:8090/api/ingestion/event/batch', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer secret',
        'Content-Type': 'application/cloudevents-batch+json',
      },
      body: JSON.stringify(eventData),
    });

    if (response && response.status != 204) {
      console.error(response);
      process.exit(1);
    } else {
      console.debug('ingestion of event data finished successfully');
      process.exit(0);
    }
  } catch (e) {
    console.error(e);
    process.exit(1);
  }
})();

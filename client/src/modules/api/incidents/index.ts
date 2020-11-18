/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'modules/request';

async function fetchInstancesByWorkflow() {
  return get('/api/incidents/byWorkflow');
}

async function fetchIncidentsByError() {
  return get('/api/incidents/byError');
}

export {fetchInstancesByWorkflow, fetchIncidentsByError};

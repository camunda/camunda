/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

export async function loadReports() {
  const response = await get('api/entities');
  const entities = await response.json();

  return filterReports(entities);
}

export async function loadReportsInCollection(id) {
  const response = await get('api/collection/' + id);
  const collection = await response.json();

  return filterReports(collection.data.entities);
}

function filterReports(data) {
  return data.filter(({entityType}) => entityType === 'report');
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, del} from 'request';

export async function loadProcesses() {
  const response = await get('/api/eventBasedProcess');
  return await response.json();
}

export async function removeProcess(id) {
  return await del(`api/eventBasedProcess/${id}`);
}

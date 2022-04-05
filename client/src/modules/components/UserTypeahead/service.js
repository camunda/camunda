/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

export async function searchIdentities(terms, excludeUserGroups) {
  const response = await get(`api/identity/search`, {terms, excludeUserGroups});

  return await response.json();
}

export async function getUser(id) {
  const response = await get(`api/identity/${id}`);
  return await response.json();
}

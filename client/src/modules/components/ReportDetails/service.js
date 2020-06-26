/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export async function loadTenants(key, versions, type) {
  const params = {versions};
  const response = await post(`api/definition/${type}/${key}/_resolveTenantsForVersions`, params);

  return await response.json();
}

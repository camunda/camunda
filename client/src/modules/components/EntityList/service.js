/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

export async function deleteEntities(entities) {
  return await post(`api/entities/delete`, entities);
}

export async function checkConflicts(entities) {
  return await post(`api/entities/delete-conflicts`, entities);
}

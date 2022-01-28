/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';

export async function fetchOperations({
  pageSize,
  searchAfter,
}: {
  pageSize: number;
  searchAfter?: [string, string];
}) {
  return request({
    url: '/api/batch-operations',
    method: 'POST',
    body: {
      pageSize,
      searchAfter,
    },
  });
}

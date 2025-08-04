/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationQuery} from 'modules/api/processInstances/operations';

function extractIdsFromQuery(query: BatchOperationQuery): {
  ids?: string[];
  excludeIds?: string[];
} {
  return {
    ids: 'ids' in query && Array.isArray(query.ids) ? query.ids : undefined,
    excludeIds:
      'excludeIds' in query && Array.isArray(query.excludeIds)
        ? query.excludeIds
        : undefined,
  };
}

export {extractIdsFromQuery};

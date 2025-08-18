/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationQuery} from 'modules/api/processInstances/operations';
import {z} from 'zod';

const filterSchema = z.object({
  ids: z.array(z.string()).optional().catch(undefined),
  excludeIds: z.array(z.string()).optional().catch(undefined),
});

function extractIdsFromQuery(query: BatchOperationQuery): {
  ids?: string[];
  excludeIds?: string[];
} {
  const result = filterSchema.safeParse(query);

  if (!result.success) {
    return {
      ids: undefined,
      excludeIds: undefined,
    };
  }

  return result.data;
}

export {extractIdsFromQuery};

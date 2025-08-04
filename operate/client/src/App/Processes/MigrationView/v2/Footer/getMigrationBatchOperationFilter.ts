/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  GetProcessDefinitionStatisticsRequestBody,
  CreateMigrationBatchOperationRequestBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';

const getMigrationBatchOperationFilter = ({
  ids,
  excludeIds,
  baseFilter = {},
}: {
  ids?: string[];
  excludeIds?: string[];
  baseFilter?: GetProcessDefinitionStatisticsRequestBody['filter'];
}): CreateMigrationBatchOperationRequestBody['filter'] => {
  const filter = {...baseFilter};

  const keyFilter =
    typeof baseFilter.processInstanceKey === 'object' &&
    baseFilter.processInstanceKey !== null
      ? {...baseFilter.processInstanceKey}
      : {};

  if (ids?.length) {
    keyFilter.$in = [...(keyFilter.$in ?? []), ...ids];
  }

  if (excludeIds?.length) {
    keyFilter.$notIn = [...(keyFilter.$notIn ?? []), ...excludeIds];
  }

  if (Object.keys(keyFilter).length > 0) {
    filter.processInstanceKey = keyFilter;
  }

  return filter;
};

export {getMigrationBatchOperationFilter};

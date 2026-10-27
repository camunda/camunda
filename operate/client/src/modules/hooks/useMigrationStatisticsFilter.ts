/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {useProcessInstanceStatisticsFilters} from 'modules/hooks/useProcessInstanceStatisticsFilters';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {buildInstanceKeyCriterion} from 'modules/utils/instances/buildInstanceKeyCriterion';

/**
 * Combines a process instances statistics filter with the migration instances selection.
 * Similar to {@linkcode useProcessInstanceStatisticsFilters} but scopes the statistics to
 * the instances a migration will actually affect.
 */
function useMigrationStatisticsFilter(): GetProcessDefinitionStatisticsRequestBody {
  const {batchOperationQuery} = processInstanceMigrationStore.state;
  const base = useProcessInstanceStatisticsFilters(
    batchOperationQuery?.conditions,
  );

  const processInstanceKey = buildInstanceKeyCriterion(
    batchOperationQuery?.ids,
    batchOperationQuery?.excludeIds,
  );

  return {
    filter: {
      ...base.filter,
      ...(processInstanceKey ? {processInstanceKey} : {}),
    },
  };
}

export {useMigrationStatisticsFilter};

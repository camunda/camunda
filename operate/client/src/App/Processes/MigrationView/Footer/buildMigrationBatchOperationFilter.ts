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
} from '@camunda/camunda-api-zod-schemas/8.8';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';

type MigrationFilterOptions = {
  baseFilter: GetProcessDefinitionStatisticsRequestBody['filter'];
  includeIds: string[];
  excludeIds: string[];
  processDefinitionKey?: string | null;
};

const buildMigrationBatchOperationFilter = ({
  baseFilter,
  includeIds,
  excludeIds,
  processDefinitionKey,
}: MigrationFilterOptions): CreateMigrationBatchOperationRequestBody['filter'] => {
  const filter: CreateMigrationBatchOperationRequestBody['filter'] = {
    ...baseFilter,
  };

  const keyCriterion = buildProcessInstanceKeyCriterion(includeIds, excludeIds);

  if (keyCriterion) {
    const existingKeyFilter = baseFilter?.processInstanceKey;

    if (typeof existingKeyFilter === 'object' && existingKeyFilter !== null) {
      filter.processInstanceKey = {
        ...existingKeyFilter,
        ...keyCriterion,
      };
    } else {
      filter.processInstanceKey = keyCriterion;
    }
  }

  if (processDefinitionKey) {
    filter.processDefinitionKey = processDefinitionKey;
  }

  return filter;
};

export {buildMigrationBatchOperationFilter};

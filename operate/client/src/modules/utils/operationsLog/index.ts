/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.8';

const formatBatchTitle = (batchOperationType?: BatchOperationType) => {
  switch (batchOperationType) {
    case 'DELETE_PROCESS_INSTANCE':
    case 'CANCEL_PROCESS_INSTANCE':
    case 'MIGRATE_PROCESS_INSTANCE':
    case 'MODIFY_PROCESS_INSTANCE':
      return 'process instances';
    case 'RESOLVE_INCIDENT':
      return 'incidents';
    case 'DELETE_DECISION_DEFINITION':
      return 'decisions';
    case 'DELETE_PROCESS_DEFINITION':
      return 'process definitions';
    case 'ADD_VARIABLE':
    case 'UPDATE_VARIABLE':
      return 'variables';
    default:
      return undefined;
  }
};

export {formatBatchTitle};

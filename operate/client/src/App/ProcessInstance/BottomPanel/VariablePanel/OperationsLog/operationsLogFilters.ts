/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ProcessInstanceOperationsLogFilterField = 'operationType' | 'entityType';

type ProcessInstanceOperationsLogFilters = {
  operationType?: string;
  entityType?: string;
};

const PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS: (keyof ProcessInstanceOperationsLogFilters)[] =
  ['operationType', 'entityType'];

export type {
  ProcessInstanceOperationsLogFilters,
  ProcessInstanceOperationsLogFilterField,
};
export {PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS};

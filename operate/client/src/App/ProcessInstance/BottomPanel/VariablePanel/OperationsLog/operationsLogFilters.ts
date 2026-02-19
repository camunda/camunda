/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

<<<<<<< HEAD
=======
type ProcessInstanceOperationsLogFilterField = 'operationType' | 'entityType';

>>>>>>> 685f9df6 (feat: add operation and entity type filter to PI Operations Log)
type ProcessInstanceOperationsLogFilters = {
  operationType?: string;
  entityType?: string;
};

<<<<<<< HEAD
type ProcessInstanceOperationsLogFilterField =
  keyof ProcessInstanceOperationsLogFilters;

const PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS: ProcessInstanceOperationsLogFilterField[] =
  ['operationType', 'entityType'];
=======
const PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS: (keyof ProcessInstanceOperationsLogFilters)[] =
  ['operationType', 'entityType'];

>>>>>>> 685f9df6 (feat: add operation and entity type filter to PI Operations Log)
export type {
  ProcessInstanceOperationsLogFilters,
  ProcessInstanceOperationsLogFilterField,
};
export {PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS};

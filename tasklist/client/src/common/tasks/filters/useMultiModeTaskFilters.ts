/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getClientConfig} from 'common/config/getClientConfig';
import {
  useTaskFilters as useTaskFiltersV1,
  type TaskFilters as TaskFiltersV1,
} from 'v1/features/tasks/filters/useTaskFilters';
import {
  useTaskFilters as useTaskFiltersV2,
  type TaskFilters as TaskFiltersV2,
} from 'v2/features/tasks/filters/useTaskFilters';

type MultiModeTaskFilters = TaskFiltersV1 | TaskFiltersV2;

const useMultiModeTaskFilters =
  getClientConfig().clientMode === 'v2' ? useTaskFiltersV2 : useTaskFiltersV1;

export {useMultiModeTaskFilters};
export type {MultiModeTaskFilters};

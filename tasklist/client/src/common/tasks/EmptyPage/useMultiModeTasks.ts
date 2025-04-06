/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getClientConfig} from 'common/config/getClientConfig';
import {useTasks as useV1Tasks} from 'v1/api/useTasks.query';
import {useTasks as useV2Tasks} from 'v2/api/useTasks.query';
import {useTaskFilters as useV1TaskFilters} from 'v1/features/tasks/filters/useTaskFilters';
import {useTaskFilters as useV2TaskFilters} from 'v2/features/tasks/filters/useTaskFilters';

const useMultiModeTasks =
  getClientConfig().clientMode === 'v1'
    ? () => {
        return useV1Tasks(useV1TaskFilters());
      }
    : () => {
        return useV2Tasks(useV2TaskFilters());
      };

export {useMultiModeTasks};

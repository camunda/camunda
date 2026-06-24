/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useElementInstancesStatistics} from './useElementInstancesStatistics';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

const executedElementsParser = (
  response: GetProcessInstanceStatisticsResponseBody,
) =>
  response.items.filter(({completed}) => {
    return completed > 0;
  });

const useExecutedElements = () => {
  return useElementInstancesStatistics(executedElementsParser);
};

export {useExecutedElements};

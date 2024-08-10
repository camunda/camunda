/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import {RequestFilters} from 'modules/utils/filter';

type ProcessInstancesStatisticsDto = {
  activityId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

type ProcessInstancesStatisticsRequest = RequestFilters & {
  excludeIds?: string[];
};

const fetchProcessInstancesStatistics = async (
  payload: ProcessInstancesStatisticsRequest,
) => {
  return requestAndParse<ProcessInstancesStatisticsDto[]>({
    url: '/api/process-instances/statistics',
    method: 'POST',
    body: payload,
  });
};

export {fetchProcessInstancesStatistics};
export type {ProcessInstancesStatisticsDto, ProcessInstancesStatisticsRequest};

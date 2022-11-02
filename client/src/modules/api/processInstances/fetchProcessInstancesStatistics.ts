/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type ProcessInstancesStatisticsDto = {
  activityId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

const fetchProcessInstancesStatistics = async (payload: any) => {
  return requestAndParse<ProcessInstancesStatisticsDto[]>({
    url: '/api/process-instances/statistics',
    method: 'POST',
    body: payload,
  });
};

export {fetchProcessInstancesStatistics};
export type {ProcessInstancesStatisticsDto};

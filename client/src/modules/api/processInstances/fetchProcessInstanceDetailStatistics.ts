/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type ProcessInstanceDetailStatisticsDto = {
  activityId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

const fetchProcessInstanceDetailStatistics = async (
  processInstanceId: ProcessInstanceEntity['id']
) => {
  return requestAndParse<ProcessInstanceDetailStatisticsDto[]>({
    url: `/api/process-instances/${processInstanceId}/statistics`,
  });
};

export {fetchProcessInstanceDetailStatistics};
export type {ProcessInstanceDetailStatisticsDto};

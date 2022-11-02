/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';
import {RequestFilters} from 'modules/utils/filter';

type ProcessInstancesDto = {
  processInstances: ProcessInstanceEntity[];
  totalCount: number;
};

type ProcessInstancesQuery = {
  query: RequestFilters;
  sorting?: {
    sortBy: string;
    sortOrder: 'desc' | 'asc';
  };
  searchAfter?: ReadonlyArray<string>;
  searchBefore?: ReadonlyArray<string>;
  pageSize?: number;
};

const fetchProcessInstances = async ({
  payload,
  signal,
}: {
  payload: ProcessInstancesQuery;
  signal?: AbortSignal;
}) => {
  return requestAndParse<ProcessInstancesDto>({
    url: '/api/process-instances',
    method: 'POST',
    body: payload,
    signal,
  });
};

async function fetchProcessInstancesByIds({
  ids,
  signal,
}: {
  ids: ProcessInstanceEntity['id'][];
  signal?: AbortSignal;
}) {
  return fetchProcessInstances({
    payload: {
      pageSize: ids.length,
      query: {
        running: true,
        finished: true,
        active: true,
        incidents: true,
        completed: true,
        canceled: true,
        ids,
      },
    },
    signal,
  });
}

export {fetchProcessInstances, fetchProcessInstancesByIds};
export type {ProcessInstancesDto};

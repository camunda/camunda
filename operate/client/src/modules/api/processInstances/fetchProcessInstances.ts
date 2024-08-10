/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  options,
}: {
  payload: ProcessInstancesQuery;
  signal?: AbortSignal;
  options?: Parameters<typeof requestAndParse>[1];
}) => {
  return requestAndParse<ProcessInstancesDto>(
    {
      url: '/api/process-instances',
      method: 'POST',
      body: payload,
      signal,
    },
    options,
  );
};

async function fetchProcessInstancesByIds(
  {
    ids,
    signal,
  }: {
    ids: ProcessInstanceEntity['id'][];
    signal?: AbortSignal;
  },
  options?: Parameters<typeof requestAndParse>[1],
) {
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
    options,
  });
}

export {fetchProcessInstances, fetchProcessInstancesByIds};
export type {ProcessInstancesDto};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, UseQueryResult} from '@tanstack/react-query';
import {
  GetJobsRequestBody,
  GetJobsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestError} from 'modules/request';
import {searchJobs} from 'modules/api/v2/jobs/searchJobs';

const JOBS_SEARCH_QUERY_KEY = 'jobsSearch';

function getQueryKey(payload: GetJobsRequestBody) {
  return [JOBS_SEARCH_QUERY_KEY, ...Object.values(payload)];
}

function useJobs<T = GetJobsResponseBody>(
  payload: GetJobsRequestBody,
  select?: (data: GetJobsResponseBody) => T,
): UseQueryResult<T, RequestError> {
  return useQuery({
    queryKey: getQueryKey(payload),
    queryFn: async () => {
      const {response, error} = await searchJobs(payload);

      if (response !== null) {
        return response;
      }

      throw error;
    },
    select,
  });
}

export {JOBS_SEARCH_QUERY_KEY, useJobs};

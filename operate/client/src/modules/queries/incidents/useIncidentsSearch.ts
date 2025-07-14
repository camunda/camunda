/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';

import type {QueryIncidentsRequestBody} from '@vzeta/camunda-api-zod-schemas';
import {searchIncidents} from 'modules/api/v2/incidents/searchIncidents.ts';

const INCIDENTS_SEARCH_QUERY_KEY = 'incidentsSearch';

const useIncidentsSearch = (params: QueryIncidentsRequestBody) => {
  return useQuery({
    queryKey: [INCIDENTS_SEARCH_QUERY_KEY, params],
    queryFn: () =>
      searchIncidents(params).then(({response, error}) => {
        if (response !== null) return response;
        throw error;
      }),
    enabled: !!params,
  });
};

export {useIncidentsSearch};

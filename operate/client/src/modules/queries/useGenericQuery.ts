/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, UseQueryOptions, UseQueryResult} from '@tanstack/react-query';
import {RequestError, RequestResult} from 'modules/request';

function useGenericQuery<RawDataT, ParsedDataT>(
  queryKey: unknown[],
  fetchFunction: () => RequestResult<RawDataT>,
  parser: (data: RawDataT) => ParsedDataT,
  options?: UseQueryOptions<ParsedDataT, RequestError>,
): UseQueryResult<ParsedDataT, RequestError> {
  return useQuery<ParsedDataT, RequestError>({
    queryKey,
    queryFn: async () => {
      const {response, error} = await fetchFunction();

      if (response !== null) {
        return parser(response);
      }

      throw error;
    },
    ...options,
  });
}

export {useGenericQuery};

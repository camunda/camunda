/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {commonApi} from 'common/api';
import {request, type RequestError} from 'common/api/request';
import type {License} from '@vzeta/camunda-api-zod-schemas/management';

function useLicense() {
  return useQuery<License, RequestError | Error>({
    queryKey: ['license'],
    queryFn: async () => {
      const {response, error} = await request(commonApi.getLicense());

      if (response !== null) {
        return await response.json();
      }

      throw error;
    },
    gcTime: Infinity,
    staleTime: Infinity,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
  });
}

export {useLicense};

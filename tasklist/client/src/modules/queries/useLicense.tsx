/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, type RequestError} from 'modules/request';
import type {License} from 'modules/types';

function useLicense() {
  return useQuery<License, RequestError | Error>({
    queryKey: ['license'],
    queryFn: async () => {
      const {response, error} = await request(api.v2.getLicense());

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

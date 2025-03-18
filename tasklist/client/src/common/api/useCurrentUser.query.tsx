/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {commonApi} from 'common/api';
import {type RequestError, request} from 'common/api/request';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/identity';

function useCurrentUser() {
  return useQuery<CurrentUser, RequestError | Error>({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const {response, error} = await request(commonApi.getCurrentUser());

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch current user');
    },
    gcTime: Infinity,
    staleTime: Infinity,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
  });
}

export {useCurrentUser};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {type RequestError, request} from 'modules/request';
import type {CurrentUser} from 'modules/types';

function useCurrentUser() {
  return useQuery<CurrentUser, RequestError | Error>({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const {response, error} = await request(api.v2.getCurrentUser());

      if (response !== null) {
        const currentUser = await response.json();
        const permissions: unknown[] | undefined = currentUser?.permissions ?? [];
        return {
          ...currentUser,
          permissions:
            permissions &&
            permissions
              .filter<string>(
                (permission): permission is string =>
                  typeof permission === 'string',
              )
              .map((permission) => permission.toLowerCase()),
        };
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

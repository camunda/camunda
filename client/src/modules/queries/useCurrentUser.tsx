/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {CurrentUser} from 'modules/types';

function useCurrentUser() {
  return useQuery<CurrentUser, RequestError | Error>({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const {response, error} = await request(api.getCurrentUser);

      if (response !== null) {
        const currentUser = await response.json();

        return {
          ...currentUser,
          permissions: currentUser?.permissions?.map((permission: string) =>
            permission.toLowerCase(),
          ),
        };
      }

      throw error ?? new Error('Could not fetch current user');
    },
    cacheTime: Infinity,
    staleTime: Infinity,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
  });
}

export {useCurrentUser};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {ProcessInstance} from 'modules/types';
import {useCurrentUser} from './useCurrentUser';

type Data = ProcessInstance[];

function useProcessInstances() {
  const {data: currentUser} = useCurrentUser();

  return useQuery<Data, RequestError>({
    queryKey: ['processInstances', currentUser?.userId],
    queryFn: async () => {
      const {response, error} = await request(
        api.searchProcessInstances({
          userId: currentUser!.userId,
          pageSize: 50,
        }),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Failed to fetch process instances');
    },
    refetchInterval: 5000,
    enabled: currentUser !== undefined,
  });
}

export {useProcessInstances};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'v1/api';
import {type RequestError, request} from 'common/api/request';
import type {ProcessInstance} from 'v1/api/types';
import {useCurrentUser} from 'common/api/useCurrentUser.query';

type Data = ProcessInstance[];

function useProcessInstances() {
  const {data: currentUser} = useCurrentUser();
  const username = currentUser?.username;

  return useQuery<Data, RequestError>({
    queryKey: ['processInstances', username],
    queryFn: async () => {
      const {response, error} = await request(
        api.searchProcessInstances({
          username: username!,
          pageSize: 50,
        }),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Failed to fetch process instances');
    },
    refetchInterval: 5000,
    enabled: username !== undefined,
  });
}

export {useProcessInstances};

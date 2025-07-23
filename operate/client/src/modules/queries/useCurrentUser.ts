/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, queryOptions} from '@tanstack/react-query';
import {getMe} from 'modules/api/v2/authentication/me';

const currentUserQueryOptions = queryOptions({
  queryKey: ['currentUser'],
  queryFn: async () => {
    const {response, error} = await getMe();

    if (response !== null) {
      return response;
    }

    throw error;
  },
  gcTime: Infinity,
  staleTime: Infinity,
  refetchIntervalInBackground: false,
  refetchOnWindowFocus: false,
});

function useCurrentUser() {
  return useQuery(currentUserQueryOptions);
}

export {useCurrentUser, currentUserQueryOptions};

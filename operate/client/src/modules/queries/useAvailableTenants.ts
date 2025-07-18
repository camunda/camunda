/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, useQueryClient} from '@tanstack/react-query';
import {currentUserQueryOptions} from './useCurrentUser';

export const useAvailableTenants = () => {
  const queryClient = useQueryClient();

  const {data} = useQuery({
    queryKey: ['availableTenants'],
    queryFn: async () => {
      const currentUser = await queryClient.ensureQueryData(
        currentUserQueryOptions,
      );

      if (!currentUser?.tenants) {
        return {};
      }

      return currentUser.tenants.reduce(
        (acc, tenant) => ({
          [tenant.tenantId]: tenant.name,
          ...acc,
        }),
        {} as Record<string, string>,
      );
    },
    gcTime: Infinity,
    staleTime: Infinity,
  });

  return data ?? {};
};

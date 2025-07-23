/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useCurrentUser} from './useCurrentUser';

const useAvailableTenants = () => {
  const {data: currentUser} = useCurrentUser();

  return useMemo<Record<string, string>>(() => {
    if (!currentUser?.tenants) {
      return {};
    }

    return currentUser.tenants.reduce(
      (acc, tenant) => ({
        [tenant.tenantId]: tenant.name,
        ...acc,
      }),
      {},
    );
  }, [currentUser?.tenants]);
};

export {useAvailableTenants};

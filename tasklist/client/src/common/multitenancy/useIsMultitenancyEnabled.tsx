/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getClientConfig} from 'common/config/getClientConfig';
import {useCurrentUser} from 'common/api/useCurrentUser.query';

function useIsMultitenancyEnabled() {
  const {data: currentUser} = useCurrentUser();
  const hasMultipleTenants = (currentUser?.tenants.length ?? 0) > 1;
  const isMultitenancyEnabled =
    getClientConfig().isMultiTenancyEnabled &&
    currentUser !== undefined &&
    hasMultipleTenants;

  return {isMultitenancyEnabled};
}

export {useIsMultitenancyEnabled};

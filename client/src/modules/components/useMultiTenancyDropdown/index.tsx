/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCurrentUser} from 'modules/queries/useCurrentUser';

function useMultiTenancyDropdown() {
  const {data: currentUser} = useCurrentUser();
  const hasMultipleTenants = (currentUser?.tenants.length ?? 0) > 1;
  const isMultiTenancyVisible =
    window.clientConfig?.isMultiTenancyEnabled &&
    currentUser !== undefined &&
    hasMultipleTenants;

  return {isMultiTenancyVisible};
}

export {useMultiTenancyDropdown};

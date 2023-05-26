/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {Permissions} from 'modules/types';

const usePermissions = (scopes: Permissions) => {
  const {data: currentUser} = useCurrentUser();

  const permissions = currentUser?.permissions;

  const hasPermission =
    permissions !== undefined &&
    permissions.some((permission) => scopes?.includes(permission));

  return {hasPermission};
};

export {usePermissions};

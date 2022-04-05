/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery} from '@apollo/client';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
import {Permissions} from 'modules/types';

const usePermissions = (scopes: Permissions) => {
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  const permissions = userData?.currentUser?.permissions;

  const hasPermission =
    permissions !== undefined &&
    permissions.some((permission) => scopes?.includes(permission));

  return {hasPermission};
};

export {usePermissions};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

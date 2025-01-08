/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Permissions} from 'modules/types';
import {useCurrentUser} from '../queries/useCurrentUser';

const usePermissions = (scopes?: Permissions) => {
  const {data: currentUser} = useCurrentUser();
  if (!currentUser) {
    return {hasPermission: false};
  }
  const permissions = currentUser.permissions;
  if (permissions === undefined) {
    return {hasPermission: true}; // FIXME
  }
  return {
    hasPermission:
      permissions.some((permission) => scopes?.includes(permission)) ?? false,
  };
};

export {usePermissions};

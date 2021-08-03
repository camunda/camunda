/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery} from '@apollo/client';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
import {Roles} from 'modules/types';

const useRoles = (scopes: Roles) => {
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  const roles = userData?.currentUser?.roles;

  const hasPermission =
    roles !== undefined && roles.some((role) => scopes?.includes(role));

  return {hasPermission};
};

export {useRoles};

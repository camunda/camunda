/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {User} from 'modules/types';

const currentUser: User = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read', 'write'],
  salesPlanType: null,
  roles: [],
  __typename: 'User',
};

const currentRestrictedUser: User = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read'],
  salesPlanType: null,
  roles: [],
  __typename: 'User',
};

const currentUserWithUnknownRole: User = {
  userId: 'demo',
  displayName: 'Demo User',
  // @ts-ignore
  permissions: ['unknown'],
  __typename: 'User',
};

const currentUserWithOutRole: User = {
  userId: 'demo',
  displayName: 'Demo User',
  // @ts-ignore
  permissions: [],
  __typename: 'User',
};

export {
  currentUser,
  currentRestrictedUser,
  currentUserWithOutRole,
  currentUserWithUnknownRole,
};

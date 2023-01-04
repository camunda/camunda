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
  c8Links: [],
  __typename: 'User',
};

const currentUserWithC8Links: User = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read', 'write'],
  salesPlanType: null,
  roles: [],
  c8Links: [
    {
      name: 'operate',
      link: 'https://link-to-operate',
    },
    {
      name: 'tasklist',
      link: 'https://link-to-tasklist',
    },
    {
      name: 'modeler',
      link: 'https://link-to-modeler',
    },
    {
      name: 'optimize',
      link: 'https://link-to-optimize',
    },
    {
      name: 'console',
      link: 'https://link-to-console',
    },
  ],
  __typename: 'User',
};

const currentRestrictedUser: User = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read'],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  __typename: 'User',
};

const currentUserWithUnknownRole: User = {
  userId: 'demo',
  displayName: 'Demo User',
  // @ts-ignore
  permissions: ['unknown'],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  __typename: 'User',
};

const currentUserWithOutRole: User = {
  userId: 'demo',
  displayName: 'Demo User',
  // @ts-ignore
  permissions: [],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  __typename: 'User',
};

export {
  currentUser,
  currentRestrictedUser,
  currentUserWithOutRole,
  currentUserWithUnknownRole,
  currentUserWithC8Links,
};

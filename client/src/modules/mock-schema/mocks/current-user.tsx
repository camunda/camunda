/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CurrentUser} from 'modules/types';

const currentUser: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read', 'write'],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
};

const currentUserWithC8Links: CurrentUser = {
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
  tenants: [],
};

const currentRestrictedUser: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read'],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
};

const currentUserWithUnknownRole: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  // @ts-expect-error done on purpose
  permissions: ['unknown'],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
};

const currentUserWithoutRole: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  // @ts-expect-error done on purpose
  permissions: [],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
};

const currentUserWithTenants: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  permissions: ['read', 'write'],
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [
    {
      id: 'tenantA',
      name: 'Tenant A',
    },
    {
      id: 'tenantB',
      name: 'Tenant B',
    },
  ],
};

export {
  currentUser,
  currentRestrictedUser,
  currentUserWithoutRole,
  currentUserWithUnknownRole,
  currentUserWithC8Links,
  currentUserWithTenants,
};

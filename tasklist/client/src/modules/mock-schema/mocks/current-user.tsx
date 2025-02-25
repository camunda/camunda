/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from 'modules/types';

const currentUser: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: [],
};

const currentUserWithC8Links: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
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
  groups: [],
};

const currentUserWithTenants: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
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
  groups: [],
};

const currentUserWithGroups: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
};

export {
  currentUser,
  currentUserWithC8Links,
  currentUserWithTenants,
  currentUserWithGroups,
};

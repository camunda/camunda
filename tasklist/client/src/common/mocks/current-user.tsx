/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.9';

const currentUser: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: {},
  tenants: [],
  groups: [],
  canLogout: true,
  authorizedComponents: ['*'],
  email: 'demo@camunda.com',
};

const currentUserWithC8Links: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: {
    operate: 'https://link-to-operate',
    tasklist: 'https://link-to-tasklist',
    modeler: 'https://link-to-modeler',
    optimize: 'https://link-to-optimize',
    console: 'https://link-to-console',
  },
  tenants: [],
  groups: [],
  canLogout: true,
  authorizedComponents: ['*'],
  email: 'demo-with-c8links@camunda.com',
};

const currentUserWithTenants: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: {},
  tenants: [
    {
      tenantId: 'tenantA',
      name: 'Tenant A',
      description: null,
    },
    {
      tenantId: 'tenantB',
      name: 'Tenant B',
      description: null,
    },
  ],
  groups: [],
  canLogout: true,
  authorizedComponents: ['*'],
  email: 'demo-with-tenants@camunda.com',
};

const currentUserWithGroups: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: {},
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
  canLogout: true,
  authorizedComponents: ['*'],
  email: 'demo-groups@camunda.com',
};

const currentUnauthorizedUser: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: {},
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
  canLogout: true,
  authorizedComponents: ['operate'],
  email: 'demo-unauthorized@camunda.com',
};

export {
  currentUser,
  currentUserWithC8Links,
  currentUserWithTenants,
  currentUserWithGroups,
  currentUnauthorizedUser,
};

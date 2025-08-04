/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/8.8';

const currentUser: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: [],
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  email: 'demo@camunda.com',
};

const currentUserWithC8Links: CurrentUser = {
  username: 'demo',
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
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  email: 'demo-with-c8links@camunda.com',
};

const currentUserWithTenants: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [
    {
      tenantId: 'tenantA',
      name: 'Tenant A',
      key: 1,
    },
    {
      tenantId: 'tenantB',
      name: 'Tenant B',
      key: 2,
    },
  ],
  groups: [],
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  email: 'demo-with-tenants@camunda.com',
};

const currentUserWithGroups: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  email: 'demo-groups@camunda.com',
};

const currentUnauthorizedUser: CurrentUser = {
  username: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
  canLogout: true,
  authorizedApplications: ['operate'],
  apiUser: false,
  email: 'demo-unauthorized@camunda.com',
};

export {
  currentUser,
  currentUserWithC8Links,
  currentUserWithTenants,
  currentUserWithGroups,
  currentUnauthorizedUser,
};

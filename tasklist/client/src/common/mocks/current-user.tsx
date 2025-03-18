/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/identity';

const currentUser: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: [],
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  userKey: 2251799813685250,
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
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  userKey: 2251799813685250,
};

const currentUserWithTenants: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [
    {
      tenantId: 'tenantA',
      name: 'Tenant A',
    },
    {
      tenantId: 'tenantB',
      name: 'Tenant B',
    },
  ],
  groups: [],
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  userKey: 2251799813685250,
};

const currentUserWithGroups: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
  canLogout: true,
  authorizedApplications: ['*'],
  apiUser: false,
  userKey: 2251799813685250,
};

const currentUnauthorizedUser: CurrentUser = {
  userId: 'demo',
  displayName: 'Demo User',
  salesPlanType: null,
  roles: [],
  c8Links: [],
  tenants: [],
  groups: ['admin', 'customer-support', 'guest'],
  canLogout: true,
  authorizedApplications: ['operate'],
  apiUser: false,
  userKey: 2251799813685250,
};

export {
  currentUser,
  currentUserWithC8Links,
  currentUserWithTenants,
  currentUserWithGroups,
  currentUnauthorizedUser,
};

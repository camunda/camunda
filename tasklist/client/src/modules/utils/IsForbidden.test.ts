/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isForbidden} from './isForbidden';
import type {CurrentUser} from 'modules/types';

describe('isForbidden', () => {
  const baseUser: CurrentUser = {
    userId: '123',
    displayName: 'Test User',
    authorizedApplications: [],
    roles: ['user'],
    salesPlanType: null,
    c8Links: [
      {name: 'tasklist', link: 'https://tasklist.example.com'},
      {name: 'operate', link: 'https://operate.example.com'},
    ],
    tenants: [{id: 'tenant1', name: 'Tenant One'}],
    groups: ['group1'],
  };

  it('should return true when authorizedApplications does not contain "tasklist" or "*"', () => {
    const user: CurrentUser = {
      ...baseUser,
      authorizedApplications: ['operate'],
    };
    expect(isForbidden(user)).toBe(true);
  });

  it('should return false when authorizedApplications contains "tasklist"', () => {
    const user: CurrentUser = {
      ...baseUser,
      authorizedApplications: ['operate', 'tasklist'],
    };
    expect(isForbidden(user)).toBe(false);
  });

  it('should return false when authorizedApplications contains "*"', () => {
    const user: CurrentUser = {
      ...baseUser,
      authorizedApplications: ['*'],
    };
    expect(isForbidden(user)).toBe(false);
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type PermissionsDto = Array<'read' | 'write'>;

type SalesPlanType =
  | 'paid'
  | 'paid-cc'
  | 'enterprise'
  | 'trial'
  | 'free'
  | 'free-to-paid-request'
  | 'in-negotiation'
  | 'internal';

type UserDto = {
  userId: string;
  displayName: string | null;
  canLogout: boolean;
  permissions?: PermissionsDto;
  roles: ReadonlyArray<string> | null;
  salesPlanType: SalesPlanType | null;
  c8Links: {
    [key in
      | 'console'
      | 'modeler'
      | 'tasklist'
      | 'operate'
      | 'optimize']?: string;
  };
  tenants: {tenantId: string; name: string}[] | null;
};

const getUser = async (options: Parameters<typeof requestAndParse>[1]) => {
  return requestAndParse<UserDto>({url: '/api/authentications/user'}, options);
};

export {getUser};
export type {UserDto};

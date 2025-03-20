/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type SalesPlanType =
  | 'paid'
  | 'paid-cc'
  | 'enterprise'
  | 'trial'
  | 'free'
  | 'free-to-paid-request'
  | 'in-negotiation'
  | 'internal';

type MeDto = {
  userId: string;
  displayName: string | null;
  canLogout: boolean;
  authorizedApplications?: Array<string>;
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

const getMe = async (options: Parameters<typeof requestAndParse>[1]) => {
  return requestAndParse<MeDto>({url: '/v2/authentication/me'}, options);
};

export {getMe};
export type {MeDto};

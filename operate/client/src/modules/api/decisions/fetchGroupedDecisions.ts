/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import type {ResourceBasedPermissionDto} from 'modules/types/operate';

type DecisionDto = {
  decisionId: string;
  name: string | null;
  decisions: {
    id: string;
    version: number;
    decisionId: string;
  }[];
  permissions?: ResourceBasedPermissionDto[] | null;
  tenantId: string;
};

const fetchGroupedDecisions = async (tenantId?: string) => {
  return requestAndParse<DecisionDto[]>({
    url: '/api/decisions/grouped',
    method: 'POST',
    body: {tenantId},
  });
};

export {fetchGroupedDecisions};
export type {DecisionDto};

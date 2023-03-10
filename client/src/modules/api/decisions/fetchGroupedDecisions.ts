/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type DecisionDto = {
  decisionId: string;
  name: string | null;
  decisions: {
    id: string;
    version: number;
    decisionId: string;
  }[];
  permissions?: PermissionDto[] | null;
};

const fetchGroupedDecisions = async () => {
  return requestAndParse<DecisionDto[]>({
    url: '/api/decisions/grouped',
  });
};

export {fetchGroupedDecisions};
export type {DecisionDto};

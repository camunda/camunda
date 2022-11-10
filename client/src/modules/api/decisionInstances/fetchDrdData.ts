/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type DrdDataDto = {
  [decisionId: string]: [
    {
      decisionInstanceId: DecisionInstanceEntity['id'];
      state: DecisionInstanceEntityState;
    }
  ];
};

const fetchDrdData = async (decisionInstanceId: string) => {
  return requestAndParse<DrdDataDto>({
    url: `/api/decision-instances/${decisionInstanceId}/drd-data`,
  });
};

export {fetchDrdData};
export type {DrdDataDto};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type DrdDataDto = {
  [decisionId: string]: [
    {
      decisionInstanceId: DecisionInstanceEntity['id'];
      state: DecisionInstanceEntityState;
    },
  ];
};

const fetchDrdData = async (decisionInstanceId: string) => {
  return requestAndParse<DrdDataDto>({
    url: `/api/decision-instances/${decisionInstanceId}/drd-data`,
  });
};

export {fetchDrdData};
export type {DrdDataDto};

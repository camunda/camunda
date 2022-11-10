/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type DecisionInstanceDto = {
  id: string;
  state: DecisionInstanceEntityState;
  decisionType: 'DECISION_TABLE' | 'LITERAL_EXPRESSION';
  decisionDefinitionId: string;
  decisionId: string;
  decisionName: string;
  decisionVersion: number;
  evaluationDate: string;
  errorMessage: string | null;
  processInstanceId: string | null;
  result: string | null;
  evaluatedInputs: Array<{
    id: string;
    name: string;
    value: string | null;
  }>;
  evaluatedOutputs: Array<{
    id: string;
    ruleIndex: number;
    ruleId: string;
    name: string;
    value: string | null;
  }>;
};

const fetchDecisionInstance = async (decisionInstanceId: string) => {
  return requestAndParse<DecisionInstanceDto>({
    url: `/api/decision-instances/${decisionInstanceId}`,
  });
};

export {fetchDecisionInstance};
export type {DecisionInstanceDto};

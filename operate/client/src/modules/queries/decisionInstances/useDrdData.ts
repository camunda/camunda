/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryKeys} from '../queryKeys';
import {searchDecisionInstances} from 'modules/api/v2/decisionInstances/searchDecisionInstances';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.8';

type DrdData = {
  [decisionDefinitionId: DecisionInstance['decisionDefinitionId']]: {
    decisionDefinitionId: DecisionInstance['decisionDefinitionId'];
    decisionEvaluationInstanceKey: DecisionInstance['decisionEvaluationInstanceKey'];
    state: DecisionInstance['state'];
  };
};

const useDrdData = (decisionEvaluationKey?: string) => {
  return useQuery({
    enabled: !!decisionEvaluationKey,
    queryKey: queryKeys.decisionInstances.drdData(decisionEvaluationKey ?? ''),
    queryFn: async () => {
      const {response, error} = await searchDecisionInstances({
        filter: {decisionEvaluationKey},
      });
      if (error) {
        throw error;
      }

      let drdData = {} as DrdData;
      for (const item of response.items) {
        // Displaying DRD data always works with the last decisionDefinitionId
        // if multiple exists for the same `decisionEvaluationKey`.
        drdData[item.decisionDefinitionId] = {
          decisionDefinitionId: item.decisionDefinitionId,
          decisionEvaluationInstanceKey: item.decisionEvaluationInstanceKey,
          state: item.state,
        };
      }
      return drdData;
    },
  });
};

export {useDrdData, type DrdData};

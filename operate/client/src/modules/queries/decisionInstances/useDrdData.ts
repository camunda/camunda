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

      const drdData = mapDecisionInstancesToDrdData(response.items);
      if (response.page.totalItems <= response.items.length) {
        return drdData;
      }

      const {response: remaining, error: remainingError} =
        await searchDecisionInstances({
          filter: {decisionEvaluationKey},
          page: {from: response.items.length, limit: response.page.totalItems},
        });
      if (remainingError) {
        throw remainingError;
      }

      return mapDecisionInstancesToDrdData(remaining.items, drdData);
    },
  });
};

const mapDecisionInstancesToDrdData = (
  instances: DecisionInstance[],
  drdData: DrdData = {},
): DrdData => {
  for (const instance of instances) {
    // Displaying DRD data always works with the last decisionDefinitionId
    // if multiple exists for the same `decisionEvaluationKey`.
    drdData[instance.decisionDefinitionId] = {
      decisionDefinitionId: instance.decisionDefinitionId,
      decisionEvaluationInstanceKey: instance.decisionEvaluationInstanceKey,
      state: instance.state,
    };
  }
  return drdData;
};

export {useDrdData, type DrdData};

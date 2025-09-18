/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchDecisionInstance} from 'modules/api/v2/decisionInstances/fetchDecisionInstance';
import {queryKeys} from '../queryKeys';
import {useQuery} from '@tanstack/react-query';

const useDecisionInstance = (decisionEvaluationInstanceKey: string) => {
  return useQuery({
    queryKey: queryKeys.decisionInstances.get(decisionEvaluationInstanceKey),
    queryFn: async () => {
      const {response, error} = await fetchDecisionInstance(
        decisionEvaluationInstanceKey,
      );
      if (response !== null) {
        return response;
      }
      throw error;
    },
  });
};

export {useDecisionInstance};

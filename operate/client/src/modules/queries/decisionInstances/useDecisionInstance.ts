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
import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';

type UnauthorizedError = {
  variant: 'unauthorized-error';
};

const useDecisionInstance = (decisionEvaluationInstanceKey: string) => {
  return useQuery<
    GetDecisionInstanceResponseBody,
    UnauthorizedError | RequestError
  >({
    queryKey: queryKeys.decisionInstances.get(decisionEvaluationInstanceKey),
    queryFn: async () => {
      const {response, error} = await fetchDecisionInstance(
        decisionEvaluationInstanceKey,
      );
      if (response !== null) {
        return response;
      }

      if (error?.response?.status === 403) {
        throw {variant: 'unauthorized-error'} satisfies UnauthorizedError;
      }

      throw error;
    },
  });
};

export {useDecisionInstance};

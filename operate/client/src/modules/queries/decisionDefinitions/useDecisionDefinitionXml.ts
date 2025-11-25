/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import {queryKeys} from '../queryKeys';
import {type DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.8';
import {fetchDecisionDefinitionXml} from 'modules/api/v2/decisionDefinitions/fetchDecisionDefinitionXml';
import type {RequestError} from 'modules/request';

type QueryOptions<T> = {
  decisionDefinitionKey?: DecisionDefinition['decisionDefinitionKey'];
  enabled?: boolean;
  select?: (result: string) => T;
};

function useDecisionDefinitionXml<T = string>(options?: QueryOptions<T>) {
  const decisionDefinitionKey = options?.decisionDefinitionKey;

  return useQuery<string, RequestError, T>({
    queryKey: queryKeys.decisionDefinitionXml.get(decisionDefinitionKey),
    staleTime: 'static',
    enabled: options?.enabled,
    select: options?.select,
    queryFn: !decisionDefinitionKey
      ? skipToken
      : async () => {
          const {response, error} = await fetchDecisionDefinitionXml(
            decisionDefinitionKey,
          );

          if (response !== null) {
            return response;
          }

          throw error;
        },
  });
}

export {useDecisionDefinitionXml};

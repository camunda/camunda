/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import {fetchDecisionDefinition} from 'modules/api/v2/decisionDefinitions/fetchDecisionDefinition.ts';

const DECISION_DEFINITION_QUERY_KEY = 'decisionDefinition';

const useDecisionDefinition = (
  decisionDefinitionKey: string,
  options?: {
    enabled?: boolean;
  },
) => {
  return useQuery({
    queryKey: [DECISION_DEFINITION_QUERY_KEY, decisionDefinitionKey],
    queryFn: decisionDefinitionKey
      ? async () => {
          const {response, error} = await fetchDecisionDefinition({
            decisionDefinitionKey,
          });
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
    enabled: options?.enabled,
  });
};

export {useDecisionDefinition};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken} from '@tanstack/react-query';
import {fetchProcessDefinition} from 'modules/api/v2/processDefinitions/fetchProcessDefinition';
import {queryKeys} from '../queryKeys';

const getUseProcessDefinitionOptions = (processDefinitionKey?: string) => {
  return {
    queryKey: queryKeys.processDefinitions.get(processDefinitionKey),
    queryFn: processDefinitionKey
      ? async () => {
          const {response, error} = await fetchProcessDefinition({
            processDefinitionKey,
          });
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
  } as const;
};

export {getUseProcessDefinitionOptions};

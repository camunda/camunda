/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchProcessSequenceFlows} from 'modules/api/v2/processInstances/sequenceFlows';
import {skipToken, useQuery, UseQueryResult} from '@tanstack/react-query';
import {GetProcessSequenceFlowsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestError} from 'modules/request';

function getQueryKey(processInstanceKey?: string) {
  return ['ProcessSequenceFlows', processInstanceKey];
}

function useProcessSequenceFlows(
  processInstanceKey?: string,
): UseQueryResult<GetProcessSequenceFlowsResponseBody, RequestError> {
  return useQuery({
    queryKey: getQueryKey(processInstanceKey),
    queryFn: !!processInstanceKey
      ? async () => {
          const {response, error} =
            await fetchProcessSequenceFlows(processInstanceKey);

          if (response !== null) {
            return response;
          }

          throw error;
        }
      : skipToken,
  });
}

export {useProcessSequenceFlows};

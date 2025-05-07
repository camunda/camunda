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

const SEQUENCE_FLOWS_QUERY_KEY = 'processSequenceFlows';

function getQueryKey(processInstanceKey?: string) {
  return [SEQUENCE_FLOWS_QUERY_KEY, processInstanceKey];
}

const processedSequenceFlowsParser = (
  sequenceFlowsResponse: GetProcessSequenceFlowsResponseBody,
) => {
  return sequenceFlowsResponse.items
    .map((sequenceFlow) => sequenceFlow.elementId)
    .filter((value, index, self) => self.indexOf(value) === index);
};

function useProcessSequenceFlows(
  processInstanceKey?: string,
): UseQueryResult<string[], RequestError> {
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
    select: processedSequenceFlowsParser,
  });
}

export {SEQUENCE_FLOWS_QUERY_KEY, useProcessSequenceFlows};

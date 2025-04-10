/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, UseQueryResult} from '@tanstack/react-query';
import {RequestError} from 'modules/request';
import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {fetchFlownodeInstancesStatistics} from 'modules/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {isEmpty} from 'lodash';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from '../processDefinitions/useProcessInstanceXml';

function getQueryKey(processInstanceKey?: string) {
  return ['flownodeInstancesStatistics', processInstanceKey];
}

const useFlownodeInstancesStatistics = <
  T = GetProcessInstanceStatisticsResponseBody,
>(
  select?: (data: GetProcessInstanceStatisticsResponseBody) => T,
  enabled: boolean = true,
): UseQueryResult<T, RequestError> => {
  const {processInstanceId} = useProcessInstancePageParams();
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const businessObjects =
    useProcessInstanceXml({
      processDefinitionKey: processDefinitionKey,
    }).data?.businessObjects ?? {};

  return useQuery({
    queryKey: getQueryKey(processInstanceId),
    queryFn:
      enabled && !!processInstanceId && !isEmpty(businessObjects)
        ? async () => {
            const {response, error} =
              await fetchFlownodeInstancesStatistics(processInstanceId);

            if (response !== null) {
              return response;
            }

            throw error;
          }
        : skipToken,
    select,
  });
};

export {useFlownodeInstancesStatistics};

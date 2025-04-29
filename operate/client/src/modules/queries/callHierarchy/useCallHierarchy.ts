/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, UseQueryResult} from '@tanstack/react-query';
import {RequestError} from 'modules/request';
import {GetProcessInstanceCallHierarchyResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {fetchCallHierarchy} from 'modules/api/v2/processInstances/fetchCallHierarchy';

function getQueryKey(processInstanceKey?: string) {
  return ['callHierarchy', processInstanceKey];
}

const useCallHierarchy = <T = GetProcessInstanceCallHierarchyResponseBody>(
  select?: (data: GetProcessInstanceCallHierarchyResponseBody) => T,
): UseQueryResult<T, RequestError> => {
  const {processInstanceId} = useProcessInstancePageParams();

  return useQuery({
    queryKey: getQueryKey(processInstanceId),
    queryFn: !!processInstanceId
      ? async () => {
          const {response, error} = await fetchCallHierarchy(processInstanceId);

          if (response !== null) {
            return response;
          }

          throw error;
        }
      : skipToken,
    select,
  });
};

export {useCallHierarchy};

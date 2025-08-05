/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {fetchCallHierarchy} from 'modules/api/v2/processInstances/fetchCallHierarchy';

const CALL_HIERARCHY_QUERY_KEY = 'callHierarchy';

function getQueryKey(processInstanceKey?: string) {
  return [CALL_HIERARCHY_QUERY_KEY, processInstanceKey];
}

const useCallHierarchy = (
  {processInstanceKey}: Pick<ProcessInstance, 'processInstanceKey'>,
  {enabled}: {enabled: boolean},
) => {
  return useQuery({
    queryKey: getQueryKey(processInstanceKey),
    queryFn: async () => {
      const {response, error} = await fetchCallHierarchy(processInstanceKey);

      if (response !== null) {
        return response;
      }

      throw error;
    },
    enabled,
  });
};

export {useCallHierarchy};

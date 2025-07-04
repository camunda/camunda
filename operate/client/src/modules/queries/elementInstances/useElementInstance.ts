/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import {fetchElementInstance} from '../../api/v2/elementInstances/fetchElementInstance';
import type {ElementInstance} from '@vzeta/camunda-api-zod-schemas';

const ELEMENT_INSTANCE_QUERY_KEY = 'elementInstance';

function getQueryKey(elementInstanceKey?: string) {
  return [ELEMENT_INSTANCE_QUERY_KEY, elementInstanceKey];
}

const useElementInstance = <T = ElementInstance>(
  elementInstanceKey: string,
  select?: (data: ElementInstance) => T,
) => {
  return useQuery({
    queryKey: getQueryKey(elementInstanceKey),
    queryFn: elementInstanceKey
      ? async () => {
          const {response, error} = await fetchElementInstance({
            elementInstanceKey,
          });

          if (response !== null) return response;
          throw error;
        }
      : skipToken,
    select,
  });
};

export {ELEMENT_INSTANCE_QUERY_KEY, useElementInstance};

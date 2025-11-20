/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';
import {useElementSelection} from './useElementSelection';

const useElementInstanceResolution = (): ElementInstance | null => {
  const {processInstanceId} = useProcessInstancePageParams();
  const {elementId, elementInstanceKey} = useElementSelection();

  // If elementInstanceKey is provided, fetch element instance by key
  const {data: elementInstanceByKey} = useElementInstance(
    elementInstanceKey ?? '',
    {
      enabled: !!elementInstanceKey,
    },
  );

  // If only elementId is provided, search for all instances
  const {data: searchResult} = useElementInstancesSearch(
    elementId ?? '',
    processInstanceId ?? '',
    undefined as unknown as ElementInstance['type'],
    {
      enabled: !!elementId && !elementInstanceKey && !!processInstanceId,
    },
  );

  if (elementInstanceByKey) {
    return elementInstanceByKey;
  }

  if (searchResult) {
    // Return the element instance only if there's exactly one result
    return searchResult.page.totalItems === 1 ? searchResult.items[0] : null;
  }

  return null;
};

export {useElementInstanceResolution};

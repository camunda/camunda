/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessDefinitionsSearch} from './useProcessDefinitionsSearch';

const DRAINING_REFETCH_INTERVAL = 5000;

type DrainingLookup = {
  byId: Set<string>;
  byKey: Set<string>;
};

function useDrainingProcessDefinitions() {
  return useProcessDefinitionsSearch<DrainingLookup>({
    staleTime: DRAINING_REFETCH_INTERVAL,
    refetchInterval: DRAINING_REFETCH_INTERVAL,
    payload: {
      filter: {
        state: 'DRAINING',
      },
    },
    select: (items) => ({
      byId: new Set(items.map((item) => item.processDefinitionId)),
      byKey: new Set(items.map((item) => item.processDefinitionKey)),
    }),
  });
}

export {useDrainingProcessDefinitions, type DrainingLookup};

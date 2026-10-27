/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';

/**
 * Returns the currently selected instance key in a process instance.
 * If an element is selected, it returns the resolved element instance key.
 * Otherwise, it returns the process instance key.
 *
 * _Note: The key is `null` if multiple instances exist for a selected element id._
 */
function useElementSelectionInstanceKey(): string | null {
  const {processInstanceId: processInstanceKey} =
    useProcessInstancePageParams();
  const {
    resolvedElementInstance,
    selectedElementInstanceKey,
    selectedElementId,
  } = useProcessInstanceElementSelection();

  if (!selectedElementId && !selectedElementInstanceKey) {
    return processInstanceKey ?? null;
  }

  switch (true) {
    case !selectedElementId && !selectedElementInstanceKey:
      return processInstanceKey ?? null;
    case !!selectedElementInstanceKey:
      return selectedElementInstanceKey;
    case !!resolvedElementInstance:
      return resolvedElementInstance.elementInstanceKey;
    default:
      return null;
  }
}

export {useElementSelectionInstanceKey};

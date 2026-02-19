/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNewTokenCountForSelectedNode} from './flowNodeSelection';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';

const useHasMultipleInstances = () => {
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();
  let {selectedInstancesCount} = useProcessInstanceElementSelection();
  selectedInstancesCount ??= 0;

  if (selectedInstancesCount === 1) {
    return newTokenCountForSelectedNode >= 1;
  }

  return selectedInstancesCount > 1 || newTokenCountForSelectedNode > 1;
};

export {useHasMultipleInstances};

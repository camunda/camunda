/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {modificationsStore} from 'modules/stores/modifications';

const createModification = ({
  scopeId,
  areFormFieldsValid,
  id,
  name,
  value,
  selectedFlowNodeName,
}: {
  scopeId: string | null;
  areFormFieldsValid: boolean;
  id: string;
  name: string;
  value: string;
  selectedFlowNodeName: string;
}) => {
  if (scopeId === null || !areFormFieldsValid || name === '' || value === '') {
    return;
  }

  const lastAddModification = modificationsStore.getLastVariableModification(
    scopeId,
    id,
    'ADD_VARIABLE',
  );

  if (
    lastAddModification === undefined ||
    lastAddModification.name !== name ||
    lastAddModification.newValue !== value
  ) {
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId,
        id,
        flowNodeName: selectedFlowNodeName,
        name,
        newValue: value,
      },
    });
  }
};

export {createModification};

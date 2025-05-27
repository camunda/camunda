/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getSelectedFlowNodeName} from 'modules/utils/flowNodeSelection';

const createModification = ({
  scopeId,
  areFormFieldsValid,
  id,
  name,
  value,
  businessObjects,
  isRootNodeSelected,
}: {
  scopeId: string | null;
  areFormFieldsValid: boolean;
  id: string;
  name: string;
  value: string;
  businessObjects?: BusinessObjects;
  isRootNodeSelected?: boolean;
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
        flowNodeName: getSelectedFlowNodeName({
          businessObjects,
          processDefinitionName:
            processInstanceDetailsStore.state.processInstance?.processName,
          isRootNodeSelected,
        }),
        name,
        newValue: value,
      },
    });
  }
};

export {createModification};

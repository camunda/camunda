/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {modificationsStore} from '../stores/modifications';

export const createEditVariableModification = ({
  scopeId,
  flowNodeName = 'flow-node-name',
  name,
  oldValue,
  newValue,
}: {
  scopeId: string;
  flowNodeName?: string;
  name: string;
  oldValue: string;
  newValue: string;
}) => {
  modificationsStore.addModification({
    type: 'variable',
    payload: {
      operation: 'EDIT_VARIABLE',
      id: name,
      scopeId,
      flowNodeName,
      name,
      oldValue,
      newValue,
    },
  });
};

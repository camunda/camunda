/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {modificationsStore} from '../stores/modifications';

const createEditVariableModification = ({
  scopeId,
  elementName = 'flow-node-name',
  name,
  oldValue,
  newValue,
}: {
  scopeId: string;
  elementName?: string;
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
      elementName,
      name,
      oldValue,
      newValue,
    },
  });
};

const createAddVariableModification = ({
  id,
  scopeId,
  elementName = 'flow-node-name',
  name,
  value,
}: {
  id?: string;
  scopeId: string;
  elementName?: string;
  name: string;
  value: string;
}) => {
  modificationsStore.addModification({
    type: 'variable',
    payload: {
      operation: 'ADD_VARIABLE',
      id: id ?? generateUniqueID(),
      scopeId,
      elementName,
      name,
      newValue: value,
    },
  });
};

export {createEditVariableModification, createAddVariableModification};

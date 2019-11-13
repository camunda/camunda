/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createVariables} from 'modules/testUtils';

export const mockProps = {
  isRunning: false,
  variables: createVariables(),
  editMode: '',
  isEditable: false,
  isLoading: false,
  onVariableUpdate: jest.fn(),
  setEditMode: jest.fn()
};

export const multipleVariableScopes = {
  ...mockProps,
  variables: null
};

export const noVariableScopes = {
  ...mockProps,
  variables: []
};

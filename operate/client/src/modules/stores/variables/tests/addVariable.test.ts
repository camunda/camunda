/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {variablesStore} from '../';
import {processInstanceDetailsStore} from '../../processInstanceDetails';
import {flowNodeSelectionStore} from '../../flowNodeSelection';
import {mockVariables, mockVariableOperation} from './mocks';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {createInstance, createOperation} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockGetOperation} from 'modules/mocks/api/getOperation';

vi.mock('modules/constants/variables', () => ({
  ...vi.importActual('modules/constants/variables'),
  MAX_VARIABLES_STORED: 5,
  MAX_VARIABLES_PER_REQUEST: 3,
}));

describe('Add Variable', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({id: '123', state: 'ACTIVE'}),
    );

    mockApplyOperation().withSuccess(mockVariableOperation);
    mockFetchVariables().withSuccess(mockVariables);
    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: '123',
    });

    await processInstanceDetailsStore.fetchProcessInstance('123');
  });

  afterEach(() => {
    variablesStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should add variable', async () => {
    expect(variablesStore.state.items).toEqual([]);
    expect(variablesStore.state.pendingItem).toBe(null);

    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });

    expect(variablesStore.state.items).toEqual([]);
    expect(variablesStore.state.pendingItem).toEqual({
      name: 'test',
      value: '1',
      isPreview: false,
      hasActiveOperation: true,
      isFirst: false,
      sortValues: null,
    });
  });

  it('should not add variable on server error', async () => {
    expect(variablesStore.state.items).toEqual([]);

    mockApplyOperation().withServerError();

    const mockOnError = vi.fn();
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: mockOnError,
    });
    expect(variablesStore.state.items).toEqual([]);
    expect(mockOnError).toHaveBeenCalled();
  });

  it('should not add variable on network error', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    expect(variablesStore.state.items).toEqual([]);

    mockApplyOperation().withNetworkError();

    const mockOnError = vi.fn();
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: mockOnError,
    });
    expect(variablesStore.state.items).toEqual([]);
    expect(mockOnError).toHaveBeenCalled();

    consoleErrorMock.mockRestore();
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {variablesStore} from '../';
import {processInstanceDetailsStore} from '../../processInstanceDetails';
import {flowNodeSelectionStore} from '../../flowNodeSelection';
import {mockVariables, mockVariableOperation} from './mocks';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance, createOperation} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockGetOperation} from 'modules/mocks/api/getOperation';

jest.mock('modules/constants/variables', () => ({
  ...jest.requireActual('modules/constants/variables'),
  MAX_VARIABLES_STORED: 5,
  MAX_VARIABLES_PER_REQUEST: 3,
}));

describe('Update Variable', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({id: '123', state: 'ACTIVE'})
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

  it('should update variable', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.items).toEqual(mockVariables);
    await variablesStore.updateVariable({
      id: '1',
      name: 'mwst',
      value: '65',
      onError: () => {},
    });
    expect(variablesStore.state.items).toEqual([
      {
        id: '2251799813725337-mwst',
        isFirst: true,
        name: 'mwst',
        value: '65',
        sortValues: ['mwst'],
        hasActiveOperation: true,
        isPreview: false,
      },
      {
        id: '2251799813725337-orderStatus',
        isFirst: false,
        name: 'orderStatus',
        value: '"NEW"',
        sortValues: ['orderStatus'],
        hasActiveOperation: false,
        isPreview: false,
      },
      {
        id: '2251799813725337-paid',
        isFirst: false,
        name: 'paid',
        value: 'true',
        sortValues: ['paid'],
        hasActiveOperation: false,
        isPreview: false,
      },
    ]);

    mockApplyOperation().withSuccess(mockVariableOperation);

    await variablesStore.updateVariable({
      id: '1',
      name: 'paid',
      value: 'false',
      onError: () => {},
    });
    expect(variablesStore.state.items).toEqual([
      {
        id: '2251799813725337-mwst',
        isFirst: true,
        name: 'mwst',
        value: '65',
        sortValues: ['mwst'],
        hasActiveOperation: true,
        isPreview: false,
      },
      {
        id: '2251799813725337-orderStatus',
        isFirst: false,
        name: 'orderStatus',
        value: '"NEW"',
        sortValues: ['orderStatus'],
        hasActiveOperation: false,
        isPreview: false,
      },
      {
        id: '2251799813725337-paid',
        isFirst: false,
        name: 'paid',
        value: 'false',
        sortValues: ['paid'],
        hasActiveOperation: true,
        isPreview: false,
      },
    ]);
  });

  it('should not update variable on server error', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.items).toEqual(mockVariables);

    mockApplyOperation().withServerError();

    const mockOnError = jest.fn();
    await variablesStore.updateVariable({
      id: '1',
      name: 'mwst',
      value: '65',
      onError: mockOnError,
    });
    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(mockOnError).toHaveBeenCalled();
  });

  it('should not update variable on network error', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.items).toEqual(mockVariables);

    mockApplyOperation().withNetworkError();

    const mockOnError = jest.fn();
    await variablesStore.updateVariable({
      id: '1',
      name: 'mwst',
      value: '65',
      onError: mockOnError,
    });
    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(mockOnError).toHaveBeenCalled();
  });
});

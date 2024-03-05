/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

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

    consoleErrorMock.mockRestore();
  });
});

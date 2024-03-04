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

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from '../index';
import {Wrapper, mockVariables} from './mocks';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {createInstance} from 'modules/testUtils';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {act} from 'react-dom/test-utils';

const instanceMock = createInstance({id: '1'});

describe('Footer', () => {
  it('should disable add variable button when loading', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});

    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
  });

  it('should disable add variable button if instance state is cancelled', async () => {
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
      state: 'CANCELED',
    });

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled(),
    );
  });

  it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    const [firstEditVariableButton] = screen.getAllByRole('button', {
      name: /edit variable/i,
    });
    expect(firstEditVariableButton).toBeInTheDocument();
    await user.click(firstEditVariableButton!);
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();

    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
  });

  it('should disable add variable button when selected flow node is not running', async () => {
    processInstanceDetailsStatisticsStore.init(instanceMock.id);
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'start',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'neverFails',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ]);
    mockFetchVariables().withSuccess([]);

    flowNodeMetaDataStore.init();
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    act(() =>
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'start',
        flowNodeInstanceId: '2',
        isMultiInstance: false,
      }),
    );

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate,
      ).toEqual(null),
    );

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() =>
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'neverFails',
        flowNodeInstanceId: '3',
        isMultiInstance: false,
      }),
    );

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate,
      ).toEqual(MOCK_TIMESTAMP),
    );

    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();

    flowNodeMetaDataStore.reset();
  });
});

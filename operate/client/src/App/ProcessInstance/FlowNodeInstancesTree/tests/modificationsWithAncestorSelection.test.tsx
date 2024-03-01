/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {createRef} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {createInstance} from 'modules/testUtils';
import {FlowNodeInstancesTree} from '..';
import {
  processInstanceId,
  multipleSubprocessesWithTwoRunningScopesMock,
  mockRunningNodeInstance,
  Wrapper,
} from './mocks';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {act} from 'react-dom/test-utils';
import {generateUniqueID} from 'modules/utils/generateUniqueID';

describe('FlowNodeInstancesTree - modifications with ancestor selection', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess({
      ...createInstance({
        id: processInstanceId,
        bpmnProcessId: 'nested_sub_process',
      }),
    });

    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'parent_sub_process',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'inner_sub_process',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'user_task',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.firstLevel,
    );

    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'nested_sub_process',
    );

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStore.state.status).toBe('fetched');
    });

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      processInstanceId,
    );

    modificationsStore.enableModificationMode();
  });

  it('should create placeholder as a child of selected ancestor (direct parent) if there are multiple running scopes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockRunningNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('inner_sub_process', '2_2');

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('inner_sub_process', '2_2');
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel1,
    );

    const [expandFirstScope] = screen.getAllByLabelText('parent_sub_process', {
      selector: "[aria-expanded='false']",
    });

    await user.type(expandFirstScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel1,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel2,
    );

    const [, expandSecondScope] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandSecondScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel2,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(3),
    );
  });

  it('should create placeholders as a child of selected ancestor (upper level parent) if there are multiple running scopes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockRunningNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('parent_sub_process', '2');

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('parent_sub_process', '2');
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel1,
    );

    const [expandFirstScope] = screen.getAllByLabelText('parent_sub_process', {
      selector: "[aria-expanded='false']",
    });

    await user.type(expandFirstScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel1,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel2,
    );

    const [, expandSecondScope] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandSecondScope!, '{arrowright}');

    await waitFor(() =>
      expect(screen.getAllByText('inner_sub_process')).toHaveLength(3),
    );

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel2,
    );

    const [expandFirstInnerSubprocess, ,] = screen.getAllByLabelText(
      'inner_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandFirstInnerSubprocess!, '{arrowright}');
    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(1),
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    await waitFor(() =>
      expect(
        screen.getAllByLabelText('inner_sub_process', {
          selector: "[aria-expanded='false']",
        }),
      ).toHaveLength(3),
    );

    const [, expandSecondInnerSubprocess] = screen.getAllByLabelText(
      'inner_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandSecondInnerSubprocess!, '{arrowright}');
    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(1),
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    await waitFor(() =>
      expect(
        screen.getAllByLabelText('inner_sub_process', {
          selector: "[aria-expanded='false']",
        }),
      ).toHaveLength(3),
    );

    const [, , expandThirdInnerSubprocess] = screen.getAllByLabelText(
      'inner_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandThirdInnerSubprocess!, '{arrowright}');
    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(1),
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    await waitFor(() =>
      expect(
        screen.getAllByLabelText('inner_sub_process', {
          selector: "[aria-expanded='false']",
        }),
      ).toHaveLength(3),
    );
  });

  it('should create placeholders as a child of selected ancestor (process instance key) if there are multiple running scopes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockRunningNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        'nested_sub_process',
        processInstanceId,
      );

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        'nested_sub_process',
        processInstanceId,
      );
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel1,
    );

    await waitFor(() =>
      expect(
        screen.getAllByLabelText('parent_sub_process', {
          selector: "[aria-expanded='false']",
        }),
      ).toHaveLength(4),
    );

    const [expandFirstScope, ,] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandFirstScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel1,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(4);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel2,
    );

    const [, expandSecondScope, ,] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandSecondScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel2,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(4);

    const [, , expandThirdScope] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandThirdScope!, '{arrowright}');

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(4);

    const [, , , expandFourthScope] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandFourthScope!, '{arrowright}');

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(4);
  });

  it('should visualize placeholders correctly after adding tokens on flow nodes that requires and does not require ancestor selection', async () => {
    render(
      <FlowNodeInstancesTree
        flowNodeInstance={mockRunningNodeInstance}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        'nested_sub_process',
        processInstanceId,
      );

      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {id: 'parent_sub_process', name: 'parent_sub_process'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: generateUniqueID(),
          parentScopeIds: {},
        },
      });
    });

    await waitFor(() =>
      expect(screen.getAllByText('parent_sub_process')).toHaveLength(4),
    );

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(3);
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createRef, useEffect} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {createInstance} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {FlowNodeInstancesTree} from '..';
import {
  processInstanceId,
  multipleSubprocessesWithTwoRunningScopesMock,
  mockRunningNodeInstance,
} from './mocks';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {act} from 'react-dom/test-utils';
import {generateUniqueID} from 'modules/utils/generateUniqueID';

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      processInstanceDetailsStore.reset();
      processInstanceDetailsDiagramStore.reset();
      flowNodeInstanceStore.reset();
      modificationsStore.reset();
      processInstanceDetailsStatisticsStore.reset();
      instanceHistoryModificationStore.reset();
    };
  }, []);

  return <ThemeProvider>{children}</ThemeProvider>;
};

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
      multipleSubprocessesWithTwoRunningScopesMock.firstLevel
    );

    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'nested_sub_process'
    );

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStore.state.status).toBe('fetched');
    });

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      processInstanceId
    );

    modificationsStore.enableModificationMode();
  });

  it('should create placeholder as a child of selected ancestor (direct parent) if there are multiple running scopes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockRunningNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('inner_sub_process', '2_2');

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('inner_sub_process', '2_2');
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel1
    );

    const [expandFirstScope] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandFirstScope!);

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel1
    );

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold parent_sub_process'));

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(2);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel2
    );

    const [, expandSecondScope] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandSecondScope!);

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel2
    );

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));

    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(3)
    );
  });

  it('should create placeholders as a child of selected ancestor (upper level parent) if there are multiple running scopes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockRunningNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('parent_sub_process', '2');

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken('parent_sub_process', '2');
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel1
    );

    const [expandFirstScope] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandFirstScope!);

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel1
    );

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold parent_sub_process'));

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(2);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel2
    );

    const [, expandSecondScope] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandSecondScope!);

    await waitFor(() =>
      expect(screen.getAllByText('inner_sub_process')).toHaveLength(3)
    );

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel2
    );

    const [expandFirstInnerSubprocess, ,] = screen.getAllByLabelText(
      'Unfold inner_sub_process'
    );

    await user.click(expandFirstInnerSubprocess!);
    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(1)
    );

    await user.click(screen.getByLabelText('Fold inner_sub_process'));

    await waitFor(() =>
      expect(
        screen.getAllByRole('button', {name: 'Unfold inner_sub_process'})
      ).toHaveLength(3)
    );

    const [, expandSecondInnerSubprocess] = screen.getAllByLabelText(
      'Unfold inner_sub_process'
    );

    await user.click(expandSecondInnerSubprocess!);
    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(1)
    );

    await user.click(screen.getByLabelText('Fold inner_sub_process'));

    await waitFor(() =>
      expect(
        screen.getAllByRole('button', {name: 'Unfold inner_sub_process'})
      ).toHaveLength(3)
    );

    const [, , expandThirdInnerSubprocess] = screen.getAllByLabelText(
      'Unfold inner_sub_process'
    );

    await user.click(expandThirdInnerSubprocess!);
    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(1)
    );

    await user.click(screen.getByLabelText('Fold inner_sub_process'));

    await waitFor(() =>
      expect(
        screen.getAllByRole('button', {name: 'Unfold inner_sub_process'})
      ).toHaveLength(3)
    );
  });

  it('should create placeholders as a child of selected ancestor (process instance key) if there are multiple running scopes', async () => {
    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockRunningNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        'nested_sub_process',
        processInstanceId
      );

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        'nested_sub_process',
        processInstanceId
      );
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel1
    );

    await waitFor(() =>
      expect(
        screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
      ).toHaveLength(4)
    );

    const [expandFirstScope, ,] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandFirstScope!);

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel1
    );

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold parent_sub_process'));

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(4);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.secondLevel2
    );

    const [, expandSecondScope, ,] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandSecondScope!);

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.thirdLevel2
    );

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold parent_sub_process'));

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(4);

    const [, , expandThirdScope] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandThirdScope!);

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold parent_sub_process'));

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(4);

    const [, , , expandFourthScope] = screen.getAllByLabelText(
      'Unfold parent_sub_process'
    );

    await user.click(expandFourthScope!);

    await user.click(screen.getByLabelText('Unfold inner_sub_process'));
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold parent_sub_process'));

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(4);
  });

  it('should visualize placeholders correctly after adding tokens on flow nodes that requires and does not require ancestor selection', async () => {
    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockRunningNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(2);

    act(() => {
      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        'nested_sub_process',
        processInstanceId
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
      expect(screen.getAllByText('parent_sub_process')).toHaveLength(4)
    );

    expect(
      screen.getAllByRole('button', {name: 'Unfold parent_sub_process'})
    ).toHaveLength(3);
  });
});

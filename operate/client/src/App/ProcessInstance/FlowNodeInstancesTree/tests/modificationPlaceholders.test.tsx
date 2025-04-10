/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRef, act} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {multiInstanceProcess} from 'modules/testUtils';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {FlowNodeInstancesTree} from '..';
import {
  multiInstanceProcessInstance,
  flowNodeInstances,
  mockFlowNodeInstance,
  multipleFlowNodeInstances,
  processId,
  processInstanceId,
  multipleSubprocessesWithNoRunningScopeMock,
  multipleSubprocessesWithOneRunningScopeMock,
  Wrapper,
} from './mocks';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('FlowNodeInstancesTree - Modification placeholders', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(multiInstanceProcessInstance);
    mockFetchProcessXML().withSuccess(multiInstanceProcess);
  });

  it('should show and remove two add modification flow nodes', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});

    mockFetchFlowNodeInstances().withSuccess(flowNodeInstances.level1!);
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        flowNodeInstance={{...mockFlowNodeInstance, state: 'ACTIVE'}}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.queryByText('Peter Join')).not.toBeInTheDocument();

    // modification icons
    expect(screen.queryByTestId('add-icon')).not.toBeInTheDocument();
    expect(
      screen.queryByText('warning-message-icon.svg'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('cancel-icon')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'peterJoin', name: 'Peter Join'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds:
            modificationsStore.generateParentScopeIds('peterJoin'),
        },
      });
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'peterJoin', name: 'Peter Join'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds:
            modificationsStore.generateParentScopeIds('peterJoin'),
        },
      });
    });

    await waitFor(() =>
      expect(screen.getAllByText('Peter Join')).toHaveLength(2),
    );

    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();

    // modification icons
    expect(screen.getAllByTestId('add-icon')).toHaveLength(2);
    expect(screen.getAllByTestId('warning-icon')).toHaveLength(2);
    expect(screen.queryByTestId('cancel-icon')).not.toBeInTheDocument();

    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)'),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.reset();
    });

    expect(screen.queryByText('Peter Join')).not.toBeInTheDocument();
    // modification icons
    expect(screen.queryByTestId('add-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('warning-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('cancel-icon')).not.toBeInTheDocument();
  });

  it('should show and remove one cancel modification flow nodes', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'peterJoin',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      processInstanceId,
    );
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockFetchFlowNodeInstances().withSuccess(multipleFlowNodeInstances);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        flowNodeInstance={{...mockFlowNodeInstance, state: 'ACTIVE'}}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      },
    );

    // modification icons
    expect(screen.queryByTestId('add-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('warning-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('cancel-icon')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.cancelAllTokens('peterJoin');
    });

    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getAllByText('Peter Join')).toHaveLength(2);

    // modification icons
    expect(await screen.findByTestId('cancel-icon')).toBeInTheDocument();
    expect(screen.queryByTestId('add-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('warning-icon')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.reset();
    });

    // modification icons
    expect(screen.queryByTestId('cancel-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('add-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('warning-icon')).not.toBeInTheDocument();
  });

  it('should create new parent scopes for a new palceholder if there are no running scopes', async () => {
    mockFetchProcessInstance().withSuccess({
      ...multiInstanceProcessInstance,
      bpmnProcessId: 'nested_sub_process',
    });

    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'parent_sub_process',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 2,
      },
      {
        activityId: 'inner_sub_process',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 2,
      },
      {
        activityId: 'user_task',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 2,
      },
    ]);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.firstLevel,
    );

    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStore.state.status).toBe('fetched');
    });

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      processInstanceId,
    );

    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={{
          ...mockFlowNodeInstance,
          state: 'INCIDENT',
          flowNodeId: 'nested_sub_process',
        }}
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
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'user_task', name: 'User Task'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds:
            modificationsStore.generateParentScopeIds('user_task'),
        },
      });
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'user_task', name: 'User Task'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds:
            modificationsStore.generateParentScopeIds('user_task'),
        },
      });
    });

    await waitFor(() =>
      expect(
        screen.getAllByLabelText('parent_sub_process', {
          selector: "[aria-expanded='false']",
        }),
      ).toHaveLength(3),
    );

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.secondLevel1,
    );

    const [expandFirstScope, expandSecondScope, expandNewScope] =
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      });

    await user.type(expandFirstScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.thirdLevel1,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('user_task')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.secondLevel2,
    );

    await user.type(expandSecondScope!, '{arrowright}');

    await waitFor(() =>
      expect(screen.getAllByText('inner_sub_process')).toHaveLength(2),
    );

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.thirdLevel2,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    await waitFor(() =>
      expect(screen.getAllByText('user_task')).toHaveLength(2),
    );

    await user.type(expandNewScope!, '{arrowright}');

    expect(screen.getAllByText('inner_sub_process')).toHaveLength(3);

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(screen.getAllByText('user_task')).toHaveLength(4);

    // fold first (existing) parent scope
    await user.type(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      })[0]!,
      '{arrowleft}',
    );

    expect(screen.getAllByText('inner_sub_process')).toHaveLength(2);
    expect(screen.getAllByText('user_task')).toHaveLength(3);

    // fold second (existing) parent scope
    await user.type(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      })[0]!,
      '{arrowleft}',
    );

    expect(screen.getAllByText('inner_sub_process')).toHaveLength(1);
    expect(screen.getAllByText('user_task')).toHaveLength(2);

    // fold new parent scope
    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('inner_sub_process')).not.toBeInTheDocument();
    expect(screen.queryByText('user_task')).not.toBeInTheDocument();
  });

  it.skip('should not create new parent scopes for a new palceholder if there is one running scopes', async () => {
    mockFetchProcessInstance().withSuccess({
      ...multiInstanceProcessInstance,
      bpmnProcessId: 'nested_sub_process',
    });

    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'parent_sub_process',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'inner_sub_process',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'user_task',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ]);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.firstLevel,
    );

    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStore.state.status).toBe('fetched');
    });
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      processInstanceId,
    );

    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={{
          ...mockFlowNodeInstance,
          state: 'ACTIVE',
          flowNodeId: 'nested_sub_process',
        }}
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
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'user_task', name: 'User Task'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds:
            modificationsStore.generateParentScopeIds('user_task'),
        },
      });
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'user_task', name: 'User Task'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds:
            modificationsStore.generateParentScopeIds('user_task'),
        },
      });
    });

    expect(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='false']",
      }),
    ).toHaveLength(2);

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.secondLevel1,
    );

    const [expandFirstScope, expandSecondScope] = screen.getAllByLabelText(
      'parent_sub_process',
      {
        selector: "[aria-expanded='false']",
      },
    );

    await user.type(expandFirstScope!, '{arrowright}');

    expect(await screen.findByText('inner_sub_process')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.thirdLevel1,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    expect(await screen.findByText('user_task')).toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.secondLevel2,
    );

    await user.type(expandSecondScope!, '{arrowright}');

    await waitFor(() =>
      expect(screen.getAllByText('inner_sub_process')).toHaveLength(2),
    );

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.thirdLevel2,
    );

    await user.type(
      screen.getByLabelText('inner_sub_process', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );

    await waitFor(() =>
      expect(screen.getAllByText('Event_1rw6vny')).toHaveLength(2),
    );

    expect(screen.getAllByText('user_task')).toHaveLength(4);

    // fold first parent scope
    await user.type(
      screen.getAllByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      })[0]!,
      '{arrowleft}',
    );

    expect(screen.getByText('inner_sub_process')).toBeInTheDocument();
    expect(screen.getAllByText('user_task')).toHaveLength(3);

    // fold second parent scope
    await user.type(
      screen.getByLabelText('parent_sub_process', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}',
    );

    expect(screen.queryByText('inner_sub_process')).not.toBeInTheDocument();
    expect(screen.queryByText('user_task')).not.toBeInTheDocument();
  });
});

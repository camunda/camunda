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
import {multiInstanceProcess} from 'modules/testUtils';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {FlowNodeInstancesTree} from '..';
import {
  multiInstanceProcessInstance,
  flowNodeInstances,
  mockFlowNodeInstance,
  multipleFlowNodeInstances,
  processInstanceId,
  multipleSubprocessesWithNoRunningScopeMock,
  multipleSubprocessesWithOneRunningScopeMock,
  Wrapper,
} from './mocks';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockNestedSubProcessBusinessObjects} from 'modules/mocks/mockNestedSubProcessBusinessObjects';
import {
  cancelAllTokens,
  generateParentScopeIds,
} from 'modules/utils/modifications';

describe('FlowNodeInstancesTree - Modification placeholders', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(multiInstanceProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);
  });

  it('should show and remove two add modification flow nodes', async () => {
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
          parentScopeIds: generateParentScopeIds({}, 'peterJoin'),
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
          parentScopeIds: generateParentScopeIds({}, 'peterJoin'),
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
      cancelAllTokens('peterJoin', 0, 0, {});
    });

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();
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

  it('should create new parent scopes for a new placeholder if there are no running scopes', async () => {
    mockFetchProcessInstance().withSuccess({
      ...multiInstanceProcessInstance,
      bpmnProcessId: 'nested_sub_process',
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.firstLevel,
    );

    mockFetchProcessDefinitionXml().withSuccess(mockNestedSubprocess);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect([
        flowNodeInstanceStore.state.status,
        processInstanceDetailsStore.state.status,
      ]).toEqual(['fetched', 'fetched']);
    });

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
      await screen.findAllByLabelText('parent_sub_process', {
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
          parentScopeIds: generateParentScopeIds(
            mockNestedSubProcessBusinessObjects,
            'user_task',
            'nested_sub_process',
          ),
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
          parentScopeIds: generateParentScopeIds(
            mockNestedSubProcessBusinessObjects,
            'user_task',
            'nested_sub_process',
          ),
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

  it('should not create new parent scopes for a new palceholder if there is one running scopes', async () => {
    mockFetchProcessInstance().withSuccess({
      ...multiInstanceProcessInstance,
      bpmnProcessId: 'nested_sub_process',
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.firstLevel,
    );

    mockFetchProcessDefinitionXml().withSuccess(mockNestedSubprocess);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect([
        flowNodeInstanceStore.state.status,
        processInstanceDetailsStore.state.status,
      ]).toEqual(['fetched', 'fetched']);
    });

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
      await screen.findAllByLabelText('parent_sub_process', {
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
          parentScopeIds: generateParentScopeIds({}, 'user_task'),
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
          parentScopeIds: generateParentScopeIds({}, 'user_task'),
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

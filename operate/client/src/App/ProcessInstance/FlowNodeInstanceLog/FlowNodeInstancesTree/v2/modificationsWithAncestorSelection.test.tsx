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
import {createInstance} from 'modules/testUtils';
import {FlowNodeInstancesTree} from '.';
import {
  processInstanceId,
  multipleSubprocessesWithTwoRunningScopesMock,
  mockRunningNodeInstance,
  Wrapper,
} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {generateUniqueID} from 'modules/utils/generateUniqueID';

describe.skip('FlowNodeInstancesTree - modifications with ancestor selection', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess({
      ...createInstance({
        id: processInstanceId,
        bpmnProcessId: 'nested_sub_process',
      }),
    });

    mockFetchFlowNodeInstances().withSuccess(
      multipleSubprocessesWithTwoRunningScopesMock.firstLevel,
    );

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect([
        flowNodeInstanceStore.state.status,
        processInstanceDetailsStore.state.status,
      ]).toEqual(['fetched', 'fetched']);
    });

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
      modificationsStore.finishAddingToken({}, 'inner_sub_process', '2_2');

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken({}, 'inner_sub_process', '2_2');
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
      modificationsStore.finishAddingToken({}, 'parent_sub_process', '2');

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken({}, 'parent_sub_process', '2');
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
        {},
        'nested_sub_process',
        processInstanceId,
      );

      modificationsStore.startAddingToken('user_task');
      modificationsStore.finishAddingToken(
        {},
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
        {},
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

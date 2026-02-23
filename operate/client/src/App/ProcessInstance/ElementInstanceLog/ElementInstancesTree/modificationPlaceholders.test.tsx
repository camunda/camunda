/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {modificationsStore} from 'modules/stores/modifications';
import {multiInstanceProcess, searchResult} from 'modules/testUtils';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {ElementInstancesTree} from './index';
import {
  multipleSubprocessesWithNoRunningScopeMock,
  multipleSubprocessesWithOneRunningScopeMock,
  Wrapper,
  mockMultiInstanceProcessInstance,
  mockNestedSubProcessInstance,
} from './mocks';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {
  cancelAllTokens,
  generateParentScopeIds,
} from 'modules/utils/modifications';
import {mockNestedSubProcessBusinessObjects} from 'modules/mocks/mockNestedSubProcessBusinessObjects';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {businessObjectsParser} from 'modules/queries/processDefinitions/useBusinessObjects';

const multiInstanceProcessDiagramModel =
  await parseDiagramXML(multiInstanceProcess);
const multiInstanceProcessBusinessObjects = businessObjectsParser({
  diagramModel: multiInstanceProcessDiagramModel,
});

const mockNestedSubprocessDiagramModel =
  await parseDiagramXML(mockNestedSubprocess);
const nestedSubprocessBusinessObjects = businessObjectsParser({
  diagramModel: mockNestedSubprocessDiagramModel,
});

describe('ElementInstancesTree - Modification placeholders', () => {
  beforeEach(async () => {
    mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess(searchResult([]));
  });

  it('should create new parent scopes for a new placeholder if there are no running scopes', async () => {
    mockFetchProcessInstance().withSuccess(mockNestedSubProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(mockNestedSubprocess);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.firstLevel,
    );

    const {user} = render(
      <ElementInstancesTree
        processInstance={mockNestedSubProcessInstance}
        businessObjects={nestedSubprocessBusinessObjects}
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
        screen.getAllByRole('treeitem', {
          name: /parent_sub_process/i,
          expanded: false,
        }),
      ).toHaveLength(3),
    );

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.secondLevel1,
    );
    mockFetchElementInstance('1').withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.firstLevel.items[0]!,
    );

    const [expandFirstScope, expandSecondScope, expandNewScope] =
      screen.getAllByRole('treeitem', {
        name: /parent_sub_process/i,
        expanded: false,
      });

    expect(expandFirstScope).toBeInTheDocument();

    await user.type(expandFirstScope!, '{arrowright}');

    await waitFor(() => {
      expect(expandFirstScope).toHaveAttribute('aria-expanded', 'true');
    });

    expect(
      screen.getByRole('treeitem', {name: 'inner_sub_process'}),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.thirdLevel1,
    );
    mockFetchElementInstance('1_2').withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.secondLevel1.items[1]!,
    );

    await user.type(
      screen.getByRole('treeitem', {
        name: 'inner_sub_process',
        expanded: false,
      }),
      '{arrowright}',
    );
    expect(
      screen.getByRole('treeitem', {name: 'user_task'}),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.secondLevel2,
    );
    mockFetchElementInstance('2').withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.firstLevel.items[1]!,
    );

    await user.type(expandSecondScope!, '{arrowright}');

    await waitFor(() =>
      expect(
        screen.getAllByRole('treeitem', {name: 'inner_sub_process'}),
      ).toHaveLength(2),
    );

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.thirdLevel2,
    );
    mockFetchElementInstance('2_2').withSuccess(
      multipleSubprocessesWithNoRunningScopeMock.secondLevel2.items[1]!,
    );

    await user.type(
      screen.getByRole('treeitem', {
        name: 'inner_sub_process',
        expanded: false,
      }),
      '{arrowright}',
    );

    await waitFor(() =>
      expect(screen.getAllByRole('treeitem', {name: 'user_task'})).toHaveLength(
        2,
      ),
    );

    await user.type(expandNewScope!, '{arrowright}');

    expect(
      screen.getAllByRole('treeitem', {name: /inner_sub_process/i}),
    ).toHaveLength(3);

    await user.type(
      screen.getByRole('treeitem', {
        name: /inner_sub_process/i,
        expanded: false,
      }),
      '{arrowright}',
    );

    expect(screen.getAllByRole('treeitem', {name: /user_task/i})).toHaveLength(
      4,
    );

    // fold first (existing) parent scope
    await user.type(expandFirstScope!, '{arrowleft}');

    expect(
      screen.getAllByRole('treeitem', {name: /inner_sub_process/i}),
    ).toHaveLength(2);
    expect(screen.getAllByRole('treeitem', {name: /user_task/i})).toHaveLength(
      3,
    );

    // fold second (existing) parent scope
    await user.type(expandSecondScope!, '{arrowleft}');

    expect(
      screen.getAllByRole('treeitem', {name: /inner_sub_process/i}),
    ).toHaveLength(1);
    expect(screen.getAllByRole('treeitem', {name: /user_task/i})).toHaveLength(
      2,
    );

    // fold new parent scope
    await user.type(expandNewScope!, '{arrowleft}');

    expect(
      screen.queryByRole('treeitem', {name: 'inner_sub_process'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('treeitem', {name: 'user_task'}),
    ).not.toBeInTheDocument();
  });

  it('should show and remove two add modification flow nodes', async () => {
    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);
    mockSearchElementInstances().withSuccess(
      searchResult([
        {
          elementInstanceKey: '2251799813686130',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'COMPLETED',
          type: 'PARALLEL_GATEWAY',
          elementId: 'peterFork',
          elementName: 'Peter Fork',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
        },
        {
          elementInstanceKey: '2251799813686156',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'ACTIVE',
          type: 'MULTI_INSTANCE_BODY',
          elementId: 'filterMapSubProcess',
          elementName: 'Filter-Map Sub Process',
          hasIncident: true,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:34.205+0000',
        },
      ]),
    );

    render(
      <ElementInstancesTree
        processInstance={mockMultiInstanceProcessInstance}
        businessObjects={multiInstanceProcessBusinessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();

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
    mockFetchProcessInstance().withSuccess(mockMultiInstanceProcessInstance);
    mockSearchElementInstances().withSuccess(
      searchResult([
        {
          elementInstanceKey: '2251799813686130',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'COMPLETED',
          type: 'EXCLUSIVE_GATEWAY',
          elementId: 'peterJoin',
          elementName: 'Peter Join',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
        },
        {
          elementInstanceKey: '2251799813686156',
          processInstanceKey: '2251799813686118',
          processDefinitionKey: '2251799813686038',
          processDefinitionId: 'multiInstanceProcess',
          state: 'ACTIVE',
          type: 'EXCLUSIVE_GATEWAY',
          elementId: 'peterJoin',
          elementName: 'Peter Join',
          hasIncident: true,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
        },
      ]),
    );

    render(
      <ElementInstancesTree
        processInstance={mockMultiInstanceProcessInstance}
        businessObjects={multiInstanceProcessBusinessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();

    // modification icons
    expect(screen.queryByTestId('add-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('warning-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('cancel-icon')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      cancelAllTokens('peterJoin', 0, 0, {});
    });

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

  it('should not create new parent scopes for a new placeholder if there is one running scopes', async () => {
    mockFetchProcessInstance().withSuccess(mockNestedSubProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(mockNestedSubprocess);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.firstLevel,
    );

    const {user} = render(
      <ElementInstancesTree
        processInstance={mockNestedSubProcessInstance}
        businessObjects={nestedSubprocessBusinessObjects}
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

    const businessObjects = await parseDiagramXML(mockNestedSubprocess);

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
            businessObjects.elementsById,
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
            businessObjects.elementsById,
            'user_task',
            'nested_sub_process',
          ),
        },
      });
    });

    expect(
      screen.getAllByRole('treeitem', {
        name: /parent_sub_process/i,
        expanded: false,
      }),
    ).toHaveLength(3);

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.secondLevel1,
    );
    mockFetchElementInstance('1').withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.firstLevel.items[0]!,
    );

    const [expandFirstScope, expandSecondScope] = screen.getAllByRole(
      'treeitem',
      {
        name: /parent_sub_process/i,
        expanded: false,
      },
    );

    await user.type(expandFirstScope!, '{arrowright}');

    await waitFor(() => {
      expect(expandFirstScope).toHaveAttribute('aria-expanded', 'true');
    });

    expect(
      screen.getByRole('treeitem', {name: 'inner_sub_process'}),
    ).toBeInTheDocument();

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.thirdLevel1,
    );
    mockFetchElementInstance('1_2').withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.secondLevel1.items[1]!,
    );

    await user.type(
      screen.getByRole('treeitem', {
        name: 'inner_sub_process',
        expanded: false,
      }),
      '{arrowright}',
    );

    expect(screen.getAllByRole('treeitem', {name: 'user_task'})).toHaveLength(
      1,
    );

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.secondLevel2,
    );
    mockFetchElementInstance('2').withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.firstLevel.items[1]!,
    );

    await user.type(expandSecondScope!, '{arrowright}');

    await waitFor(() =>
      expect(
        screen.getAllByRole('treeitem', {name: 'inner_sub_process'}),
      ).toHaveLength(2),
    );

    mockSearchElementInstances().withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.thirdLevel2,
    );
    mockFetchElementInstance('2_2').withSuccess(
      multipleSubprocessesWithOneRunningScopeMock.secondLevel2.items[1]!,
    );

    await user.type(
      screen.getByRole('treeitem', {
        name: 'inner_sub_process',
        expanded: false,
      }),
      '{arrowright}',
    );

    await waitFor(() =>
      expect(
        screen.getAllByRole('treeitem', {name: 'Event_1rw6vny'}),
      ).toHaveLength(2),
    );
    expect(screen.getAllByRole('treeitem', {name: 'user_task'})).toHaveLength(
      2,
    );

    // fold first parent scope
    await user.type(expandFirstScope!, '{arrowleft}');

    expect(
      screen.getByRole('treeitem', {name: 'inner_sub_process'}),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('treeitem', {name: 'user_task'})).toHaveLength(
      1,
    );

    // fold second parent scope
    await user.type(
      screen.getByRole('treeitem', {
        name: /parent_sub_process/i,
        expanded: true,
      }),
      '{arrowleft}',
    );

    expect(
      screen.queryByRole('treeitem', {name: 'inner_sub_process'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('treeitem', {name: 'user_task'}),
    ).not.toBeInTheDocument();
  });
});

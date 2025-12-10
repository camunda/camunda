/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from 'react';
import {render, screen} from 'modules/testing-library';
import {open} from 'modules/mocks/diagrams';
import {Wrapper, mockNestedSubProcessesInstance} from './mocks';
import {ElementInstancesTree} from './index';
import {modificationsStore} from 'modules/stores/modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {businessObjectsParser} from 'modules/queries/processDefinitions/useBusinessObjects';

const nestedSubProcessesXml = open('NestedSubProcesses.bpmn');
const diagramModel = await parseDiagramXML(nestedSubProcessesXml);
const businessObjects = businessObjectsParser({diagramModel});

describe('ElementInstancesTree - Nested Subprocesses', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(mockNestedSubProcessesInstance);
    mockFetchProcessDefinitionXml().withSuccess(
      open('NestedSubProcesses.bpmn'),
    );

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [],
    });

    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchElementInstances().withSuccess({
      items: [
        {
          elementInstanceKey: '2251799813686130',
          processInstanceKey: '227539842356787',
          processDefinitionKey: '39480256723678',
          processDefinitionId: 'NestedSubProcesses',
          state: 'COMPLETED',
          type: 'START_EVENT',
          elementId: 'StartEvent_1',
          elementName: 'Start Event 1',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
        },
        {
          elementInstanceKey: '2251799813686156',
          processInstanceKey: '227539842356787',
          processDefinitionKey: '39480256723678',
          processDefinitionId: 'NestedSubProcesses',
          state: 'ACTIVE',
          type: 'SERVICE_TASK',
          elementId: 'ServiceTask',
          elementName: 'Service Task',
          hasIncident: false,
          tenantId: '<default>',
          startDate: '2020-08-18T12:07:33.953+0000',
        },
      ],
      page: {totalItems: 2},
    });
  });

  it('should add parent placeholders (ADD_TOKEN)', async () => {
    const {user} = render(
      <ElementInstancesTree
        processInstance={mockNestedSubProcessesInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('Nested Sub Processes')).toBeInTheDocument();
    expect(screen.getByText('Start Event 1')).toBeInTheDocument();
    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'UserTask', name: 'User Task'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {
            SubProcess_1: generateUniqueID(),
            SubProcess_2: generateUniqueID(),
          },
        },
      });
    });

    expect(await screen.findByText('Sub Process 1')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 1', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('Sub Process 2')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 2', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(screen.getByText('User Task')).toBeInTheDocument();

    act(() => {
      modificationsStore.disableModificationMode();
    });

    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();
  });

  it('should add parent placeholders (MOVE_TOKEN)', async () => {
    mockFetchProcessInstance().withSuccess(mockNestedSubProcessesInstance);

    const {user} = render(
      <ElementInstancesTree
        processInstance={mockNestedSubProcessesInstance}
        businessObjects={businessObjects}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('Nested Sub Processes')).toBeInTheDocument();
    expect(screen.getByText('Start Event 1')).toBeInTheDocument();
    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          scopeIds: [generateUniqueID(), generateUniqueID()],
          flowNode: {id: 'StartEvent_1', name: 'Start Event 1'},
          targetFlowNode: {id: 'UserTask', name: 'User Task'},
          affectedTokenCount: 2,
          visibleAffectedTokenCount: 2,
          parentScopeIds: {
            SubProcess_1: generateUniqueID(),
            SubProcess_2: generateUniqueID(),
          },
        },
      });
    });

    expect(await screen.findByText('Sub Process 1')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 1', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(await screen.findByText('Sub Process 2')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Sub Process 2', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}',
    );
    expect(screen.getAllByText('User Task')).toHaveLength(2);

    act(() => {
      modificationsStore.disableModificationMode();
    });

    expect(screen.queryByText('Sub Process 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Sub Process 2')).not.toBeInTheDocument();
    expect(screen.queryByText('User Task')).not.toBeInTheDocument();
  });
});

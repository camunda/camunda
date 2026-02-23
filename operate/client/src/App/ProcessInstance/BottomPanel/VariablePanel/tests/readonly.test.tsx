/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {getWrapper, mockProcessInstance} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {Paths} from 'modules/Routes';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';

const selectedElementInstance: ElementInstance = {
  elementInstanceKey: '2',
  elementId: 'Activity_0qtp1k6',
  elementName: 'Activity',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionId: 'someKey',
  processInstanceKey: '1',
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

describe('VariablePanel readonly', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  it('should be readonly if root node is selected and no add/move modification is created yet', async () => {
    modificationsStore.enableModificationMode();
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {totalItems: 1},
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable testVariableName/i}),
    ).not.toBeInTheDocument();
  });

  it('should be readonly if root node is selected and only cancel modifications exist', async () => {
    modificationsStore.enableModificationMode();
    modificationsStore.cancelToken('some-element-id', 'some-instance-key', {});
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {totalItems: 1},
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable testVariableName/i}),
    ).not.toBeInTheDocument();
  });

  it('should be readonly for existing nodes without add/move modifications', async () => {
    modificationsStore.enableModificationMode();
    mockFetchElementInstance('2').withSuccess(selectedElementInstance);
    mockSearchVariables().withSuccess({
      items: [createVariable({scopeKey: '2'})],
      page: {totalItems: 1},
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper([
        `${Paths.processInstance('1')}?elementId=Activity_0qtp1k6&elementInstanceKey=2`,
      ]),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable testVariableName/i}),
    ).not.toBeInTheDocument();
  });
});

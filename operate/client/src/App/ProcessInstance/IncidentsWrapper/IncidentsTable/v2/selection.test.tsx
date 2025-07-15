/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '.';
import {
  createIncident,
  createInstance,
  createProcessInstance,
} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {Wrapper, incidentsMock, firstIncident} from './mocks';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

describe('Selection', () => {
  it('should deselect selected incident', async () => {
    mockFetchProcessInstance().withSuccess(createInstance());
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
      }),
    );
    incidentsStore.setIncidents({
      ...incidentsMock,
      incidents: [firstIncident],
      count: 1,
    });
    selectFlowNode(
      {},
      {
        flowNodeId: firstIncident.flowNodeId,
        isMultiInstance: false,
      },
    );

    const {user} = render(<IncidentsTable />, {wrapper: Wrapper});
    expect(screen.getByRole('row', {selected: true})).toBeInTheDocument();

    await user.click(screen.getByRole('row', {selected: true}));
    expect(screen.getByRole('row', {selected: false})).toBeInTheDocument();
  });

  it('should select single incident when multiple incidents are selected', async () => {
    mockFetchProcessInstance().withSuccess(createInstance());
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
      }),
    );
    const incidents = [
      createIncident({flowNodeId: 'myTask'}),
      createIncident({flowNodeId: 'myTask'}),
    ];

    incidentsStore.setIncidents({...incidentsMock, incidents});
    selectFlowNode(
      {},
      {
        flowNodeId: 'myTask',
        isMultiInstance: false,
      },
    );

    const {user} = render(<IncidentsTable />, {wrapper: Wrapper});
    expect(screen.getAllByRole('row', {selected: true})).toHaveLength(2);

    const [firstRow] = screen.getAllByRole('row', {
      name: /condition error/i,
    });

    expect(firstRow).toBeInTheDocument();
    await user.click(firstRow!);

    expect(
      screen.getByRole('row', {
        name: /condition error/i,
        selected: true,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: /condition error/i,
        selected: false,
      }),
    ).toBeInTheDocument();
  });
});

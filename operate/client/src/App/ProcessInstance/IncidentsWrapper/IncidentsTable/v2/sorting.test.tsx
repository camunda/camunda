/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '.';
import {createIncident, createInstance} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {Wrapper, incidentsMock, shortError} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';

describe('Sorting', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(
      createInstance({permissions: ['UPDATE_PROCESS_INSTANCE']}),
    );
  });

  it('should enable sorting for all', async () => {
    incidentsStore.setIncidents(incidentsMock);
    render(<IncidentsTable />, {wrapper: Wrapper});

    expect(screen.getByText('Job Id')).toBeEnabled();
    expect(screen.getByText('Incident Type')).toBeEnabled();
    expect(screen.getByText('Failing Flow Node')).toBeEnabled();
    expect(screen.getByText('Job Id')).toBeEnabled();
    expect(screen.getByText('Creation Date')).toBeEnabled();
    expect(screen.getByText('Error Message')).toBeEnabled();
    expect(await screen.findByText('Operations'));
  });

  it('should disable sorting for jobId', () => {
    const incidents = [
      createIncident({
        errorType: {
          name: 'Error A',
          id: 'ERROR-A',
        },
        errorMessage: shortError,
        flowNodeId: 'Task A',
        flowNodeInstanceId: 'flowNodeInstanceIdA',
        jobId: null,
      }),
    ];

    incidentsStore.setIncidents({...incidentsMock, incidents, count: 1});

    render(<IncidentsTable />, {wrapper: Wrapper});
    expect(
      screen.getByRole('button', {name: 'Sort by Creation Date'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by Job Id'}),
    ).not.toBeInTheDocument();
  });
});

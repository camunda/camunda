/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '../index';
import {createIncident} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {Wrapper, incidentsMock, shortError} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

describe('Sorting', () => {
  it('should enable sorting for all', () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    incidentsStore.setIncidents(incidentsMock);
    render(<IncidentsTable />, {wrapper: Wrapper});

    expect(screen.getByText('Job Id')).toBeEnabled();
    expect(screen.getByText('Incident Type')).toBeEnabled();
    expect(screen.getByText('Failing Flow Node')).toBeEnabled();
    expect(screen.getByText('Job Id')).toBeEnabled();
    expect(screen.getByText('Creation Date')).toBeEnabled();
    expect(screen.getByText('Error Message')).toBeEnabled();
    expect(screen.getByText('Operations')).toBeEnabled();
  });

  it('should disable sorting for jobId', () => {
    mockFetchProcessDefinitionXml().withSuccess('');
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsTable} from '../index';
import {createIncident} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {Wrapper, incidentsMock, shortError} from './mocks';

describe('Sorting', () => {
  it('should enable sorting for all', () => {
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
    expect(screen.getByRole('button', {name: 'Sort by Job Id'})).toBeDisabled();
  });
});

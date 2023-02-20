/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {IncidentsWrapper} from '../index';
import {mockIncidents, Wrapper} from './mocks';
import {incidentsStore} from 'modules/stores/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';

describe('IncidentsFilter', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await incidentsStore.fetchIncidents('1');

    incidentsStore.setIncidentBarOpen(true);
  });

  it('should render the table', async () => {
    render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    const table = within(await screen.findByRole('table'));

    expect(table.getByText(/^Incident Type/)).toBeInTheDocument();
    expect(table.getByText(/^Failing Flow Node/)).toBeInTheDocument();
    expect(table.getByText(/^Job Id/)).toBeInTheDocument();
    expect(table.getByText(/^Creation Date/)).toBeInTheDocument();
    expect(table.getByText(/^Error Message/)).toBeInTheDocument();
    expect(table.getByText(/^Operations/)).toBeInTheDocument();
  });

  it('should render the filters', async () => {
    render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    const errorFilters = within(
      await screen.findByTestId(/incidents-by-errortype/i)
    );
    const flowNodeFilters = within(
      screen.getByTestId(/incidents-by-flownode/i)
    );

    expect(errorFilters.getByText(/^condition error/i)).toBeInTheDocument();
    expect(errorFilters.getByText(/^Extract value error/)).toBeInTheDocument();
    expect(
      flowNodeFilters.getByText(/^flowNodeId_exclusiveGateway/)
    ).toBeInTheDocument();
    expect(
      flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
    ).toBeInTheDocument();
    expect(screen.getByText(/^Clear All/)).toBeInTheDocument();
  });
});

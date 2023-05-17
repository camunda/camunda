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
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {name: /filter by incident type/i})
    );

    expect(
      screen.getByRole('option', {
        name: /^condition error/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {
        name: /^Extract value error/i,
      })
    ).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: /filter by flow node/i})
    );

    expect(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/i,
      })
    ).toBeInTheDocument();

    expect(screen.getByText(/^Reset Filters/)).toBeInTheDocument();
  });
});

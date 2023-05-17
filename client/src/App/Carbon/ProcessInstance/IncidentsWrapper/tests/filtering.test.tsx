/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {IncidentsWrapper} from '../index';
import {Wrapper, mockIncidents, mockResolvedIncidents} from './mocks';
import {incidentsStore} from 'modules/stores/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {act} from 'react-dom/test-utils';

describe('Filtering', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await incidentsStore.fetchIncidents('1');

    incidentsStore.setIncidentBarOpen(true);
  });

  it('should not have active filters by default', () => {
    render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(
      screen.queryByRole('button', {
        name: /clear all selected items/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should filter the incidents when errorTypes are selected', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    const table = within(await screen.findByRole('table'));

    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
    expect(table.getByText(/Extract value errortype/)).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: /filter by incident type/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^Condition errortype/,
      })
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
    expect(
      table.queryByText(/Extract value errortype/)
    ).not.toBeInTheDocument();
  });

  it('should filter the incidents when flowNodes are selected', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    const table = within(await screen.findByRole('table'));

    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(table.getByText(/flowNodeId_exclusiveGateway/)).toBeInTheDocument();
    expect(table.getByText(/flowNodeId_alwaysFailingTask/)).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: /filter by flow node/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      })
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(table.getByText(/flowNodeId_exclusiveGateway/)).toBeInTheDocument();
    expect(
      table.queryByText(/flowNodeId_alwaysFailingTask/)
    ).not.toBeInTheDocument();
  });

  it('should filter the incidents when both errorTypes & flowNodes are selected', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByRole('row')).toHaveLength(3);
    await user.click(
      await screen.findByRole('button', {name: /filter by incident type/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^Condition errortype/,
      })
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);

    await user.click(
      await screen.findByRole('button', {name: /filter by flow node/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      })
    );
    expect(screen.getAllByRole('row')).toHaveLength(1);
    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      })
    );
    expect(screen.getAllByRole('row')).toHaveLength(2);
  });

  it('should remove filter when only related incident gets resolved', async () => {
    const {user, rerender} = render(
      <IncidentsWrapper setIsInTransition={jest.fn()} />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getAllByRole('row')).toHaveLength(3);

    await user.click(
      await screen.findByRole('button', {name: /filter by flow node/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      })
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      })
    );

    expect(screen.getAllByRole('row')).toHaveLength(3);

    // incident is resolved
    mockFetchProcessInstanceIncidents().withSuccess(mockResolvedIncidents);

    await act(() => incidentsStore.fetchIncidents('1'));

    rerender(<IncidentsWrapper setIsInTransition={jest.fn()} />);

    expect(
      screen.queryByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      })
    ).not.toBeInTheDocument();

    expect(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      })
    ).toBeInTheDocument();

    expect(screen.getAllByRole('row')).toHaveLength(2);
  });

  it('should drop all filters when clicking the clear all button', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByRole('row')).toHaveLength(3);

    await user.click(
      await screen.findByRole('button', {name: /filter by flow node/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      })
    );

    await user.click(
      await screen.findByRole('button', {name: /filter by incident type/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: /^Condition errortype/,
      })
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);

    await user.click(screen.getByText(/^Reset Filters/));

    expect(screen.getAllByRole('row')).toHaveLength(3);
  });
});

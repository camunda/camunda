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

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
  });

  it('should filter the incidents when errorTypes are selected', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    const table = within(await screen.findByRole('table'));

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
    expect(table.getByText(/Extract value errortype/)).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
        /^Condition errortype/
      )
    );

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
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

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    expect(table.getByText(/flowNodeId_exclusiveGateway/)).toBeInTheDocument();
    expect(table.getByText(/flowNodeId_alwaysFailingTask/)).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId(/incidents-by-flownode/i)).getByText(
        /^flowNodeId_exclusiveGateway/
      )
    );

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
    expect(table.getByText(/flowNodeId_exclusiveGateway/)).toBeInTheDocument();
    expect(
      table.queryByText(/flowNodeId_alwaysFailingTask/)
    ).not.toBeInTheDocument();
  });

  it('should filter the incidents when both errorTypes & flowNodes are selected', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
    await user.click(
      within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
        /^Condition errortype/
      )
    );
    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);

    const flowNodeFilters = within(
      screen.getByTestId(/incidents-by-flownode/i)
    );

    await user.click(
      flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
    );
    expect(screen.queryAllByLabelText(/^incident/i)).toHaveLength(0);
    await user.click(
      flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
    );
    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
  });

  it('should remove filter when only related incident gets resolved', async () => {
    const {user, rerender} = render(
      <IncidentsWrapper setIsInTransition={jest.fn()} />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

    const flowNodeFilters = within(
      screen.getByTestId(/incidents-by-flownode/i)
    );

    await user.click(flowNodeFilters.getByText(/^flowNodeId_exclusiveGateway/));
    await user.click(
      flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
    );
    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

    // incident is resolved
    mockFetchProcessInstanceIncidents().withSuccess(mockResolvedIncidents);

    await act(() => incidentsStore.fetchIncidents('1'));

    rerender(<IncidentsWrapper setIsInTransition={jest.fn()} />);

    expect(
      flowNodeFilters.queryByText(/^flowNodeId_exclusiveGateway/)
    ).not.toBeInTheDocument();
    expect(
      flowNodeFilters.getByText(/^flowNodeId_alwaysFailingTask/)
    ).toBeInTheDocument();
    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);
  });

  it('should drop all filters when clicking the clear all button', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);

    await user.click(
      within(screen.getByTestId(/incidents-by-flownode/i)).getByText(
        /^flowNodeId_exclusiveGateway/
      )
    );
    await user.click(
      within(screen.getByTestId(/incidents-by-errortype/i)).getByText(
        /^Condition errortype/
      )
    );
    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(1);

    await user.click(screen.getByText(/^Clear All/));

    expect(screen.getAllByLabelText(/^incident/i)).toHaveLength(2);
  });
});

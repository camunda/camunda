/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      }),
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
      await screen.findByRole('combobox', {name: /filter by incident type/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^Condition errortype/,
      }),
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(table.getByText(/Condition errortype/)).toBeInTheDocument();
    expect(
      table.queryByText(/Extract value errortype/),
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
      await screen.findByRole('combobox', {name: /filter by flow node/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      }),
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);
    expect(table.getByText(/flowNodeId_exclusiveGateway/)).toBeInTheDocument();
    expect(
      table.queryByText(/flowNodeId_alwaysFailingTask/),
    ).not.toBeInTheDocument();
  });

  it('should filter the incidents when both errorTypes & flowNodes are selected', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByRole('row')).toHaveLength(3);
    await user.click(
      await screen.findByRole('combobox', {name: /filter by incident type/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^Condition errortype/,
      }),
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);

    await user.click(
      await screen.findByRole('combobox', {name: /filter by flow node/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      }),
    );
    expect(screen.getAllByRole('row')).toHaveLength(1);
    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      }),
    );
    expect(screen.getAllByRole('row')).toHaveLength(2);
  });

  it('should remove filter when only related incident gets resolved', async () => {
    const {user, rerender} = render(
      <IncidentsWrapper setIsInTransition={jest.fn()} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getAllByRole('row')).toHaveLength(3);

    await user.click(
      await screen.findByRole('combobox', {name: /filter by flow node/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      }),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      }),
    );

    expect(screen.getAllByRole('row')).toHaveLength(3);

    // incident is resolved
    mockFetchProcessInstanceIncidents().withSuccess(mockResolvedIncidents);

    await act(() => incidentsStore.fetchIncidents('1'));

    rerender(<IncidentsWrapper setIsInTransition={jest.fn()} />);

    expect(
      screen.queryByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      }),
    ).not.toBeInTheDocument();

    expect(
      screen.getByRole('option', {
        name: /^flowNodeId_alwaysFailingTask/,
      }),
    ).toBeInTheDocument();

    expect(screen.getAllByRole('row')).toHaveLength(2);
  });

  it('should drop all filters when clicking the clear all button', async () => {
    const {user} = render(<IncidentsWrapper setIsInTransition={jest.fn()} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByRole('row')).toHaveLength(3);

    await user.click(
      await screen.findByRole('combobox', {name: /filter by flow node/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^flowNodeId_exclusiveGateway/,
      }),
    );

    await user.click(
      await screen.findByRole('combobox', {name: /filter by incident type/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: /^Condition errortype/,
      }),
    );

    expect(screen.getAllByRole('row')).toHaveLength(2);

    await user.click(screen.getByText(/^Reset Filters/));

    expect(screen.getAllByRole('row')).toHaveLength(3);
  });
});

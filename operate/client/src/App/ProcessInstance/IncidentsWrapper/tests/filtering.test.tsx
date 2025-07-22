/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {IncidentsWrapper} from '../index';
import {Wrapper, mockIncidents, mockResolvedIncidents} from './mocks';
import {incidentsStore} from 'modules/stores/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {act} from 'react';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {createInstance, createProcessInstance} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';

describe('Filtering', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess(createInstance());
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
      }),
    );

    await incidentsStore.fetchIncidents('1');

    incidentsStore.setIncidentBarOpen(true);
  });

  it('should not have active filters by default', () => {
    render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByRole('button', {
        name: /clear all selected items/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should filter the incidents when errorTypes are selected', async () => {
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

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
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

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
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

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
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
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

    rerender(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
    );

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
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
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

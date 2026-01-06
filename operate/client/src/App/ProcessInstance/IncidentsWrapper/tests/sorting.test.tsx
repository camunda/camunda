/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {IncidentsWrapper} from '../index';
import {Wrapper, mockIncidents} from './mocks';
import {incidentsStore} from 'modules/stores/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {createInstance, createProcessInstance} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';

describe('Sorting', () => {
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

  it('should sort by incident type', async () => {
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    let [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/Condition errortype/);
    expect(secondRow).toHaveTextContent(/Extract value errortype/);

    await user.click(
      screen.getByRole('button', {
        name: /sort by incident type/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=errorType%2Bdesc$/,
    );

    [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/Extract value errortype/);
    expect(secondRow).toHaveTextContent(/Condition errortype/);

    await user.click(
      screen.getByRole('button', {
        name: /sort by incident type/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=errorType%2Basc$/,
    );

    [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/Condition errortype/);
    expect(secondRow).toHaveTextContent(/Extract value errortype/);
  });

  it('should sort by flow node', async () => {
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    let [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);
    expect(secondRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);

    await user.click(
      screen.getByRole('button', {
        name: /sort by failing flow node/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=flowNodeName%2Bdesc$/,
    );

    [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);
    expect(secondRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);

    await user.click(
      screen.getByRole('button', {
        name: /sort by failing flow node/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=flowNodeName%2Basc$/,
    );

    [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);
    expect(secondRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);
  });

  it('should sort by creation time', async () => {
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    let [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/flowNodeId_exclusiveGateway/);
    expect(secondRow).toHaveTextContent(/flowNodeId_alwaysFailingTask/);

    await user.click(
      screen.getByRole('button', {
        name: /sort by creation date/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=creationTime%2Basc$/,
    );

    [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/2019-03-01 14:26:19/);
    expect(secondRow).toHaveTextContent(/2022-03-01 14:26:19/);

    await user.click(
      screen.getByRole('button', {
        name: /sort by creation date/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=creationTime%2Bdesc$/,
    );

    [, firstRow, secondRow] = screen.getAllByRole('row');
    expect(firstRow).toHaveTextContent(/2022-03-01 14:26:19/);
    expect(secondRow).toHaveTextContent(/2019-03-01 14:26:19/);
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {IncidentsWrapper} from '../index';
import {Wrapper, mockIncidents} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';

describe('Sorting', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceV2().withSuccess(mockProcessInstance);
    mockSearchProcessInstances().withSuccess({
      page: {totalItems: 1},
      items: [mockProcessInstance],
    });
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      mockIncidents,
    );
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      mockIncidents,
    );
    mockSearchIncidentsByProcessInstance(':instanceId').withSuccess(
      mockIncidents,
    );

    incidentsPanelStore.setPanelOpen(true);
  });

  it('should sort by incident type', async () => {
    const {user} = render(
      <IncidentsWrapper
        processInstance={mockProcessInstance}
        setIsInTransition={vi.fn()}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('search')).toHaveTextContent('');

    await user.click(
      await screen.findByRole('button', {
        name: /sort by incident type/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=errorType%2Bdesc$/,
    );

    await user.click(
      await screen.findByRole('button', {
        name: /sort by incident type/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=errorType%2Basc$/,
    );
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

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('search')).toHaveTextContent('');

    await user.click(
      await screen.findByRole('button', {
        name: /sort by creation date/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=creationTime%2Basc$/,
    );

    await user.click(
      await screen.findByRole('button', {
        name: /sort by creation date/i,
      }),
    );

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?sort=creationTime%2Bdesc$/,
    );
  });
});

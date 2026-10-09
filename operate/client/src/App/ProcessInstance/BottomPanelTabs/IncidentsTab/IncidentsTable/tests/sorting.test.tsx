/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '..';
import {createProcessInstance} from 'modules/testUtils';
import {render, screen, within} from 'modules/testing-library';
import {Wrapper, firstIncident, incidentsMock} from './mocks';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

describe('Sorting', () => {
  beforeEach(() => {
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
        parentProcessInstanceKey: null,
        parentElementInstanceKey: null,
        rootProcessInstanceKey: null,
        tags: [],
      }),
    );
  });

  it('should enable sorting for all', async () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );

    const withinHeaderRow = within(screen.getAllByRole('row')[0]!);

    expect(withinHeaderRow.getByText('Error message')).toBeInTheDocument();
    expect(withinHeaderRow.getByText('Type')).toBeEnabled();
    expect(withinHeaderRow.getByText('Failing Element')).toBeEnabled();
    expect(withinHeaderRow.getByText('Created')).toBeEnabled();
    expect(await withinHeaderRow.findByText('Operations')).toBeInTheDocument();
  });

  it('should disable sorting for errorMessage', () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );

    expect(
      screen.getByRole('button', {name: 'Sort by Type'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by Error message'}),
    ).not.toBeInTheDocument();
  });

  it('should not sort when a non sortable header is clicked', async () => {
    const {user} = render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );

    const withinHeaderRow = within(screen.getAllByRole('row')[0]!);

    // given the API rejects ordering by errorMessage, clicking the header must
    // not write a sort param that would be sent on the next request
    await user.click(withinHeaderRow.getByText('Error message'));

    expect(screen.getByTestId('search')).toHaveTextContent(/^$/);

    await user.click(withinHeaderRow.getByText('Type'));

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?sort=errorType%2Bdesc',
    );
  });

  it('should disable sorting for elementName', () => {
    const incidents = [{...firstIncident, jobKey: ''}];

    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidents}
      />,
      {wrapper: Wrapper},
    );
    expect(
      screen.getByRole('button', {name: 'Sort by Created'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by Failing Element'}),
    ).not.toBeInTheDocument();
  });
});

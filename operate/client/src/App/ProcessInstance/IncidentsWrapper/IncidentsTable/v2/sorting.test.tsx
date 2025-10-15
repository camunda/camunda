/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '.';
import {createInstance, createProcessInstance} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {Wrapper, firstIncident, incidentsMock} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

describe('Sorting', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(
      createInstance({permissions: ['UPDATE_PROCESS_INSTANCE']}),
    );
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
      }),
    );
  });

  it('should enable sorting for all', async () => {
    render(
      <IncidentsTable processInstanceKey="1" incidents={incidentsMock} />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText('Job Id')).toBeEnabled();
    expect(screen.getByText('Incident Type')).toBeEnabled();
    expect(screen.getByText('Failing Element')).toBeEnabled();
    expect(screen.getByText('Job Id')).toBeEnabled();
    expect(screen.getByText('Creation Date')).toBeEnabled();
    expect(screen.getByText('Error Message')).toBeEnabled();
    expect(await screen.findByText('Operations')).toBeInTheDocument();
  });

  it('should disable sorting for jobKey', () => {
    const incidents = [{...firstIncident, jobKey: ''}];

    render(<IncidentsTable processInstanceKey="1" incidents={incidents} />, {
      wrapper: Wrapper,
    });
    expect(
      screen.getByRole('button', {name: 'Sort by Creation Date'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by Job Id'}),
    ).not.toBeInTheDocument();
  });
});

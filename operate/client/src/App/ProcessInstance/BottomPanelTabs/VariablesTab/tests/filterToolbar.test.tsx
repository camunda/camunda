/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {render, screen} from 'modules/testing-library';
import {createVariable, searchResult} from 'modules/testUtils';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {getWrapper, mockProcessInstance} from './mocks';
import {modificationsStore} from 'modules/stores/modifications';

describe('VariablesTab - filter toolbar', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    mockFetchElementInstancesStatistics().withSuccess({items: []});
    mockFetchElementInstancesStatistics().withSuccess({items: []});
    mockFetchElementInstancesStatistics().withSuccess({items: []});

    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchJobs().withSuccess(searchResult([]));
    mockSearchVariables().withSuccess(searchResult([createVariable()]));
  });

  it('hides the search and filter toolbar in modification mode', async () => {
    modificationsStore.enableModificationMode();

    render(<VariablesTab />, {wrapper: getWrapper()});

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    expect(
      screen.queryByRole('group', {name: 'Variable filter'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('searchbox', {name: 'Search variables by name'}),
    ).not.toBeInTheDocument();
  });

  it('shows the search and filter toolbar outside modification mode', async () => {
    render(<VariablesTab />, {wrapper: getWrapper()});

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
    expect(
      screen.getByRole('group', {name: 'Variable filter'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('searchbox', {name: 'Search variables by name'}),
    ).toBeInTheDocument();
  });
});

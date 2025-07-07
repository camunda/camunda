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
import {variablesStore} from 'modules/stores/variables';
import Variables from './index';
import {getWrapper, mockVariables, mockMetaData} from './mocks';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockProcessInstanceDeprecated} from './mocks';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

describe('Skeleton', () => {
  it('should display empty content if there are no variables', async () => {
    mockFetchVariables().withSuccess([]);
    mockFetchProcessDefinitionXml({processDefinitionKey: '123'}).withSuccess(
      mockProcessXml,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'StartEvent_1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    });

    flowNodeMetaDataStore.setMetaData(mockMetaData);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
    expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should display skeleton on initial load', async () => {
    mockFetchVariables().withSuccess(mockVariables);
    mockFetchProcessDefinitionXml({processDefinitionKey: '123'}).withSuccess(
      mockProcessXml,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'StartEvent_1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    });

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: getWrapper()});

    expect(screen.getByTestId('variables-skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
  });
});

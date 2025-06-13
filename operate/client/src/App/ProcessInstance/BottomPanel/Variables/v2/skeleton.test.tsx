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
import {getWrapper, mockVariables, mockMetaData} from './mocks';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {VariablePanel} from '../../VariablePanel/v2';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockVariablesV2} from '../index.setup';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

describe('Skeleton', () => {
  it('should display empty content if there are no variables', async () => {
    mockFetchVariables().withSuccess([]);
    mockSearchVariables().withSuccess({
      items: [],
    });
    flowNodeMetaDataStore.setMetaData(mockMetaData);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<VariablePanel />, {wrapper: getWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
    expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should display skeleton on initial load', async () => {
    mockFetchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariablesV2);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<VariablePanel />, {wrapper: getWrapper()});

    expect(screen.getByTestId('variables-skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
  });
});

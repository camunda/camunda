/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper, mockMetaData} from './mocks';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {VariablePanel} from '../../VariablePanel/v2';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

describe('Skeleton', () => {
  it('should display empty content if there are no variables', async () => {
    mockFetchVariables().withSuccess([]);
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    flowNodeMetaDataStore.setMetaData(mockMetaData);

    render(<VariablePanel />, {
      wrapper: getWrapper(),
    });
    expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
  });
});

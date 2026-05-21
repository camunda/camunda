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
import {ProcessInstanceHeader} from '../index';
import {mockInstance, Wrapper} from './index.setup';
import {createUser, mockProcessXML, searchResult} from 'modules/testUtils';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockMe} from 'modules/mocks/api/v2/me';

describe('InstanceHeader - Business ID', () => {
  beforeEach(() => {
    mockQueryBatchOperationItems().withSuccess(searchResult([]));
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(createUser());
  });

  it('should render business ID when present', async () => {
    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockInstance,
          businessId: 'order-12345',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.getByText('Business ID')).toBeInTheDocument();
    expect(screen.getByText('order-12345')).toBeInTheDocument();
  });

  it('should not render business ID when null', async () => {
    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockInstance,
          businessId: null,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Business ID')).not.toBeInTheDocument();
  });
});

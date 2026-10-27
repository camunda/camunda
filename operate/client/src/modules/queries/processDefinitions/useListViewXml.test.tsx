/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {useListViewXml} from './useListViewXml';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';

describe('useListViewXml', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  it('should get flowNodeFilterOptions', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXml);

    const {result} = renderHook(
      () =>
        useListViewXml({
          processDefinitionKey: '27589024892748902347',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(result.current.data?.flowNodeFilterOptions).toEqual([
      {label: 'EndEvent_0crvjrk', id: 'EndEvent_0crvjrk'},
      {label: 'StartEvent_1', id: 'StartEvent_1'},
      {label: 'userTask', id: 'userTask'},
    ]);
  });
});

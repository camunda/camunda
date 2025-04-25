/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {TimeStampPill} from './index';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {useEffect} from 'react';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.mock('modules/utils/bpmn');

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  mockFetchFlowNodeInstances().withSuccess({});
  mockFetchProcessDefinitionXml().withSuccess('');

  useEffect(() => {
    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
    return () => {
      flowNodeTimeStampStore.reset();
      flowNodeInstanceStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

describe('TimeStampPill', () => {
  it('should render "Show" / "Hide" label', async () => {
    const {user} = render(<TimeStampPill />, {wrapper: Wrapper});

    await waitFor(() => {
      expect(screen.getByLabelText('Show End Date')).toBeEnabled();
    });

    await user.click(screen.getByLabelText('Show End Date'));

    expect(await screen.findByLabelText('Hide End Date')).toBeInTheDocument();
  });

  it('should be disabled if diagram and instance execution history is not loaded', async () => {
    render(<TimeStampPill />, {wrapper: Wrapper});

    expect(screen.getByRole('switch')).toBeDisabled();

    await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  });
});

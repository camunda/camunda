/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {ExecutionCountToggle} from './index';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

vi.mock('modules/utils/bpmn');

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

describe('ExecutionCountToggle', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should toggle visibility when clicked', async () => {
    const {user} = render(<ExecutionCountToggle />, {wrapper: Wrapper});

    await waitFor(() => {
      expect(screen.getByLabelText('Execution count')).toBeEnabled();
    });

    const toggle = screen.getByLabelText('Execution count');
    expect(toggle).not.toBeChecked();

    await user.click(toggle);

    expect(toggle).toBeChecked();

    await user.click(toggle);

    expect(toggle).not.toBeChecked();
  });

  it('should be disabled if diagram is not loaded', async () => {
    render(<ExecutionCountToggle />, {wrapper: Wrapper});

    expect(screen.getByRole('switch')).toBeDisabled();

    await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  });
});

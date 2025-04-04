/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InputOutputMappings} from './index';
import {render, screen} from 'modules/testing-library';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {act, useEffect} from 'react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      flowNodeSelectionStore.reset();
    };
  }, []);

  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <ProcessDefinitionKeyContext.Provider value="123">
        {children}
      </ProcessDefinitionKeyContext.Provider>
    </QueryClientProvider>
  );
};

describe('Input Mappings', () => {
  beforeEach(() =>
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    ),
  );

  it('should display input mappings', async () => {
    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Event_0bonl61',
      });
    });

    const {rerender} = render(<InputOutputMappings type="Input" />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Activity_0qtp1k6',
      });
    });

    rerender(<InputOutputMappings type="Input" />);

    expect(await screen.findByText(/local variable name/i)).toBeInTheDocument();
    expect(screen.getByText(/variable assignment value/i)).toBeInTheDocument();
    expect(screen.getByText(/localVariable1/i)).toBeInTheDocument();
    expect(screen.getByText(/= "test1"/i)).toBeInTheDocument();
    expect(screen.getByText(/localVariable2/i)).toBeInTheDocument();
    expect(screen.getByText(/= "test2"/i)).toBeInTheDocument();
  });

  it('should display output mappings', async () => {
    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Event_0bonl61',
      });
    });

    const {rerender} = render(<InputOutputMappings type="Input" />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Activity_0qtp1k6',
      });
    });

    rerender(<InputOutputMappings type="Output" />);
    expect(
      await screen.findByText(/process variable name/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/variable assignment value/i)).toBeInTheDocument();
    expect(screen.getByText(/outputTest/i)).toBeInTheDocument();
    expect(screen.getByText(/= 2/i)).toBeInTheDocument();
  });

  it.each([
    {
      type: 'Input',
      message:
        'Input mappings are defined while modelling the diagram. They are used to create new local variables inside the flow node scope with the specified assignment.',
    },
    {
      type: 'Output',
      message:
        'Output mappings are defined while modelling the diagram. They are used to control the variable propagation from the flow node scope. Process variables in the parent scopes are created/updated with the specified assignment.',
    },
  ])(
    'should display/hide information banner for input/output mappings',
    async ({type, message}) => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Activity_0qtp1k6',
      });

      const {user, rerender} = render(
        <InputOutputMappings type={type as 'Input' | 'Output'} />,
        {
          wrapper: Wrapper,
        },
      );
      expect(screen.getByText(message)).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: /close/}));

      expect(screen.queryByText(message)).not.toBeInTheDocument();

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );
      rerender(<InputOutputMappings type={type as 'Input' | 'Output'} />);
      expect(screen.queryByText(message)).not.toBeInTheDocument();
    },
  );
});

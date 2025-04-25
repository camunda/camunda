/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useBusinessObjects} from './useBusinessObjects';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

describe('useBusinessObjects', () => {
  const businessObjects = {
    Activity_0qtp1k6: {
      $type: 'bpmn:CallActivity',
      extensionElements: {
        $type: 'bpmn:ExtensionElements',
        values: [
          {
            $type: 'zeebe:calledElement',
            processId: 'called-element-test',
            propagateAllChildVariables: 'false',
          },
          {
            $children: [
              {
                $type: 'zeebe:input',
                source: '= "test1"',
                target: 'localVariable1',
              },
              {
                $type: 'zeebe:input',
                source: '= "test2"',
                target: 'localVariable2',
              },
              {
                $type: 'zeebe:output',
                source: '= 2',
                target: 'outputTest',
              },
            ],
            $type: 'zeebe:ioMapping',
          },
        ],
      },
      id: 'Activity_0qtp1k6',
    },
    Event_0bonl61: {
      $type: 'bpmn:EndEvent',
      id: 'Event_0bonl61',
    },
    StartEvent_1: {
      $type: 'bpmn:StartEvent',
      id: 'StartEvent_1',
    },
  };

  const Wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch business objects successfully', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useBusinessObjects(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(businessObjects);
  });

  it('should handle server error while fetching business objects', async () => {
    mockFetchProcessDefinitionXml().withServerError();

    const {result} = renderHook(() => useBusinessObjects(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
  });

  it('should handle network error while fetching business objects', async () => {
    mockFetchProcessDefinitionXml().withNetworkError();

    const {result} = renderHook(() => useBusinessObjects(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
  });

  it('should handle empty data', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');

    const {result} = renderHook(() => useBusinessObjects(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).not.toBeDefined();
  });

  it('should handle loading state', async () => {
    mockFetchProcessDefinitionXml().withDelay(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useBusinessObjects(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isLoading).toBe(true));
  });
});

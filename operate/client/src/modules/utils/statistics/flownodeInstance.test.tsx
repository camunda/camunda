/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {renderHook} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {getStatisticsByFlowNode} from './flownodeInstances';
import {modificationsStore} from 'modules/stores/modifications';
import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';

describe('getStatisticsByFlowNode', () => {
  const businessObjects: BusinessObjects = {
    StartEvent_1: {
      $type: 'bpmn:StartEvent',
      id: 'StartEvent_1',
      name: 'Start Event',
    },
    Activity_0qtp1k6: {
      $type: 'bpmn:UserTask',
      id: 'Activity_0qtp1k6',
      name: 'User Task',
    },
    Event_0bonl61: {
      id: 'Event_0bonl61',
      name: 'End Event',
      $type: 'bpmn:EndEvent',
      $parent: {
        id: 'Process_1',
        $type: 'bpmn:Process',
        name: 'Process 1',
      },
    },
  };

  const Wrapper = ({children}: {children: React.ReactNode}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
      };
    }, []);

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

  beforeEach(async () => {
    jest.clearAllMocks();
  });

  it('should return statistics for valid flow nodes', () => {
    const data = [
      {
        flowNodeId: 'StartEvent_1',
        active: 5,
        incidents: 2,
        completed: 10,
        canceled: 1,
      },
      {
        flowNodeId: 'Activity_0qtp1k6',
        active: 3,
        incidents: 1,
        completed: 7,
        canceled: 0,
      },
    ];

    const {result} = renderHook(
      () => getStatisticsByFlowNode(data, businessObjects),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current).toEqual({
      StartEvent_1: {
        active: 5,
        filteredActive: 5,
        incidents: 2,
        completed: 10,
        completedEndEvents: 0,
        canceled: 1,
      },
      Activity_0qtp1k6: {
        active: 3,
        filteredActive: 3,
        incidents: 1,
        completed: 7,
        completedEndEvents: 0,
        canceled: 0,
      },
    });
  });

  it('should handle missing business objects', () => {
    const data = [
      {
        flowNodeId: 'StartEvent_1',
        active: 5,
        incidents: 2,
        completed: 10,
        canceled: 1,
      },
      {
        flowNodeId: 'missing_node',
        active: 3,
        incidents: 1,
        completed: 7,
        canceled: 0,
      },
    ];

    const {result} = renderHook(
      () => getStatisticsByFlowNode(data, businessObjects),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current).toEqual({
      StartEvent_1: {
        active: 5,
        filteredActive: 5,
        incidents: 2,
        completed: 10,
        completedEndEvents: 0,
        canceled: 1,
      },
    });
  });

  it('should handle modification mode', () => {
    modificationsStore.enableModificationMode();

    const data = [
      {
        flowNodeId: 'StartEvent_1',
        active: 5,
        incidents: 2,
        completed: 10,
        canceled: 1,
      },
    ];

    const {result} = renderHook(
      () => getStatisticsByFlowNode(data, businessObjects),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current).toEqual({
      StartEvent_1: {
        active: 5,
        filteredActive: 5,
        incidents: 2,
        completed: 0,
        completedEndEvents: 0,
        canceled: 0,
      },
    });
  });

  it('should handle end events correctly', () => {
    const data = [
      {
        flowNodeId: 'Event_0bonl61',
        active: 0,
        incidents: 0,
        completed: 10,
        canceled: 0,
      },
    ];

    const {result} = renderHook(
      () => getStatisticsByFlowNode(data, businessObjects),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current).toEqual({
      Event_0bonl61: {
        active: 0,
        filteredActive: 0,
        incidents: 0,
        completed: 0,
        completedEndEvents: 10,
        canceled: 0,
      },
    });
  });

  it('should return an empty object if no valid data is provided', () => {
    const data = [
      {
        flowNodeId: 'node1',
        active: 5,
        incidents: 2,
        completed: 10,
        canceled: 1,
      },
    ];

    const {result} = renderHook(
      () => getStatisticsByFlowNode(data, businessObjects),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current).toEqual({});
  });
});

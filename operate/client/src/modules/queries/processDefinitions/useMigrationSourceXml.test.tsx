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
import {useMigrationSourceXml} from './useMigrationSourceXml';
import {open} from 'modules/mocks/diagrams';
import {processesStore} from 'modules/stores/processes/processes.migration';

vi.mock('modules/stores/processes/processes.migration');

const mockedProcessesStore = vi.mocked(processesStore);

describe('useMigrationSourceXml', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  it('should filter selectable flow nodes', async () => {
    mockedProcessesStore.getSelectedProcessDetails.mockReturnValue({
      key: '27589024892748902347',
      processName: 'orderProcess',
      version: '1.0.0',
      versionTag: null,
      bpmnProcessId: 'orderProcess',
    });

    mockFetchProcessDefinitionXml().withSuccess(open('instanceMigration.bpmn'));

    const {result} = renderHook(
      () =>
        useMigrationSourceXml({
          processDefinitionKey: '27589024892748902347',
          bpmnProcessId: 'orderProcess',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(
      result.current.data?.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual([
      'checkPayment',
      'ExclusiveGateway',
      'requestForPayment',
      'shippingSubProcess',
      'shipArticles',
      'ParallelGateway_1',
      'ParallelGateway_2',
      'MessageInterrupting',
      'TimerInterrupting',
      'MessageNonInterrupting',
      'TimerNonInterrupting',
      'confirmDelivery',
      'MessageIntermediateCatch',
      'TimerIntermediateCatch',
      'MessageEventSubProcess',
      'TaskX',
      'MessageStartEvent',
      'TimerEventSubProcess',
      'TaskY',
      'TimerStartEvent',
      'ErrorEventSubProcess',
      'ErrorStartEvent',
      'MessageReceiveTask',
      'ParallelGateway_3',
      'ParallelGateway_4',
      'BusinessRuleTask',
      'ScriptTask',
      'SendTask',
      'EventBasedGateway',
      'IntermediateTimerEvent',
      'SignalIntermediateCatch',
      'SignalBoundaryEvent',
      'SignalEventSubProcess',
      'SignalStartEvent',
      'MultiInstanceSubProcess',
      'MultiInstanceTask',
      'EscalationEventSubProcess',
      'EscalationStartEvent',
      'CompensationBoundaryEvent',
      'CompensationTask',
    ]);
  });

  it('should filter selectable flow nodes (ParticipantMigrationA)', async () => {
    mockedProcessesStore.getSelectedProcessDetails.mockReturnValue({
      key: '27589024892748902347',
      processName: 'ParticipantMigrationA',
      version: '1.0.0',
      versionTag: null,
      bpmnProcessId: 'ParticipantMigrationA',
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('ParticipantMigration_v1.bpmn'),
    );

    const {result} = renderHook(
      () =>
        useMigrationSourceXml({
          processDefinitionKey: '27589024892748902347',
          bpmnProcessId: 'ParticipantMigrationA',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(
      result.current.data?.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['TaskA']);
  });

  it('should filter selectable flow nodes (ParticipantMigrationB)', async () => {
    mockedProcessesStore.getSelectedProcessDetails.mockReturnValue({
      key: '27589024892748902347',
      processName: 'ParticipantMigrationB',
      version: '1.0.0',
      versionTag: null,
      bpmnProcessId: 'ParticipantMigrationB',
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('ParticipantMigration_v1.bpmn'),
    );

    const {result} = renderHook(
      () =>
        useMigrationSourceXml({
          processDefinitionKey: '27589024892748902347',
          bpmnProcessId: 'ParticipantMigrationB',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(
      result.current.data?.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['TaskB']);
  });
});

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

describe('useMigrationSourceXml', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  it('should filter selectable elements', async () => {
    mockFetchProcessDefinitionXml().withSuccess(open('instanceMigration.bpmn'));

    const {result} = renderHook(
      () =>
        useMigrationSourceXml({
          processDefinitionKey: '27589024892748902347',
          processDefinitionId: 'orderProcess',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(
      result.current.data?.selectableElements.map((element) => element.id),
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

  it('should filter selectable elements (ParticipantMigrationA)', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('ParticipantMigration_v1.bpmn'),
    );

    const {result} = renderHook(
      () =>
        useMigrationSourceXml({
          processDefinitionKey: '27589024892748902347',
          processDefinitionId: 'ParticipantMigrationA',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(
      result.current.data?.selectableElements.map((element) => element.id),
    ).toEqual(['TaskA']);
  });

  it('should filter selectable elements (ParticipantMigrationB)', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('ParticipantMigration_v1.bpmn'),
    );

    const {result} = renderHook(
      () =>
        useMigrationSourceXml({
          processDefinitionKey: '27589024892748902347',
          processDefinitionId: 'ParticipantMigrationB',
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(
      result.current.data?.selectableElements.map((element) => element.id),
    ).toEqual(['TaskB']);
  });
});

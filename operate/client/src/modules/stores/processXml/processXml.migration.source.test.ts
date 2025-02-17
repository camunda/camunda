/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from '@testing-library/react';
import {processXmlStore} from './processXml.migration.source';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';
import {processesStore} from '../processes/processes.migration';

jest.mock('modules/stores/processes/processes.migration');

describe('stores/processXml/processXml.migration.source', () => {
  afterEach(() => {
    processXmlStore.reset();
  });

  it('should filter selectable flow nodes', async () => {
    // @ts-expect-error
    processesStore.getSelectedProcessDetails.mockReturnValue({
      bpmnProcessId: 'orderProcess',
    });

    mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
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
    // @ts-expect-error
    processesStore.getSelectedProcessDetails.mockReturnValue({
      bpmnProcessId: 'ParticipantMigrationA',
    });

    mockFetchProcessXML().withSuccess(open('ParticipantMigration_v1.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['TaskA']);
  });

  it('should filter selectable flow nodes (ParticipantMigrationB)', async () => {
    // @ts-expect-error
    processesStore.getSelectedProcessDetails.mockReturnValue({
      bpmnProcessId: 'ParticipantMigrationB',
    });

    mockFetchProcessXML().withSuccess(open('ParticipantMigration_v1.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['TaskB']);
  });
});

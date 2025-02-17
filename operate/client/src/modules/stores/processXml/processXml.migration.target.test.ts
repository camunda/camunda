/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from '@testing-library/react';
import {processXmlStore} from './processXml.migration.target';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';
import {processesStore} from '../processes/processes.migration';

jest.mock('modules/stores/processes/processes.migration', () => ({
  processesStore: {
    migrationState: {
      selectedTargetProcess: {
        bpmnProcessId: undefined,
      },
    },
  },
}));

describe('stores/processXml/processXml.migration.target', () => {
  afterEach(() => {
    processXmlStore.reset();
    // clear mocked data
    processesStore.migrationState.selectedTargetProcess!.bpmnProcessId = '';
  });

  it('should filter selectable flow nodes', async () => {
    // mock bpmnProcessId
    processesStore.migrationState.selectedTargetProcess!.bpmnProcessId =
      'orderProcess';

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
    // mock bpmnProcessId
    processesStore.migrationState.selectedTargetProcess!.bpmnProcessId =
      'ParticipantMigrationA';

    mockFetchProcessXML().withSuccess(open('ParticipantMigration_v1.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['TaskA']);
  });

  it('should filter selectable flow nodes (ParticipantMigrationB)', async () => {
    // mock bpmnProcessId
    processesStore.migrationState.selectedTargetProcess!.bpmnProcessId =
      'ParticipantMigrationB';

    mockFetchProcessXML().withSuccess(open('ParticipantMigration_v1.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['TaskB']);
  });

  it('should return true for isTargetSelected', async () => {
    // mock bpmnProcessId
    processesStore.migrationState.selectedTargetProcess!.bpmnProcessId =
      'orderProcess';

    expect(processXmlStore.isTargetSelected).toBe(false);

    mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(processXmlStore.isTargetSelected).toBe(true);
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
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {processInstanceMigrationMappingStore} from './processInstanceMigrationMapping';
import {waitFor} from '@testing-library/react';

jest.mock('modules/stores/processes/processes.migration', () => ({
  processesStore: {
    migrationState: {selectedTargetProcess: {bpmnProcessId: 'orderProcess'}},
    getSelectedProcessDetails: () => ({bpmnProcessId: 'orderProcess'}),
  },
}));

describe('processInstanceMigrationMappingStore', () => {
  afterEach(() => {
    processInstanceMigrationMappingStore.reset();
  });

  it('should provide auto mapped flow nodes', async () => {
    // Fetch migration source diagram
    mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));
    processXmlMigrationSourceStore.fetchProcessXml();
    await waitFor(() =>
      expect(processXmlMigrationSourceStore.state.status).toBe('fetched'),
    );

    // Fetch migration target diagram
    mockFetchProcessXML().withSuccess(open('instanceMigration_v2.bpmn'));
    processXmlMigrationTargetStore.fetchProcessXml();
    await waitFor(() =>
      expect(processXmlMigrationTargetStore.state.status).toBe('fetched'),
    );

    const {isAutoMappable, autoMappableFlowNodes} =
      processInstanceMigrationMappingStore;

    /*
     * Expect the following elements to be mappable,
     * because they are the only ones with the same id and type.
     */
    expect(autoMappableFlowNodes).toEqual([
      {
        id: 'checkPayment',
        type: 'bpmn:ServiceTask',
      },
      {
        id: 'ExclusiveGateway',
        type: 'bpmn:ExclusiveGateway',
      },

      {id: 'shipArticles', type: 'bpmn:UserTask'},
      {
        id: 'ParallelGateway_1',
        type: 'bpmn:ParallelGateway',
      },
      {
        id: 'ParallelGateway_2',
        type: 'bpmn:ParallelGateway',
      },
      {
        id: 'MessageInterrupting',
        type: 'bpmn:BoundaryEvent',
      },
      {
        id: 'TimerNonInterrupting',
        type: 'bpmn:BoundaryEvent',
      },
      {
        id: 'MessageIntermediateCatch',
        type: 'bpmn:IntermediateCatchEvent',
      },
      {
        id: 'MessageEventSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'TaskX',
        type: 'bpmn:ServiceTask',
      },
      {
        id: 'MessageStartEvent',
        type: 'bpmn:StartEvent',
      },
      {
        id: 'TimerEventSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'TimerStartEvent',
        type: 'bpmn:StartEvent',
      },
      {
        id: 'ErrorEventSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'ErrorStartEvent',
        type: 'bpmn:StartEvent',
      },
      {
        id: 'MessageReceiveTask',
        type: 'bpmn:ReceiveTask',
      },
      {
        id: 'ParallelGateway_3',
        type: 'bpmn:ParallelGateway',
      },
      {
        id: 'ParallelGateway_4',
        type: 'bpmn:ParallelGateway',
      },
      {
        id: 'BusinessRuleTask',
        type: 'bpmn:BusinessRuleTask',
      },
      {
        id: 'ScriptTask',
        type: 'bpmn:ScriptTask',
      },
      {
        id: 'SendTask',
        type: 'bpmn:SendTask',
      },
      {
        id: 'EventBasedGateway',
        type: 'bpmn:EventBasedGateway',
      },
      {
        id: 'IntermediateTimerEvent',
        type: 'bpmn:IntermediateCatchEvent',
      },
      {
        id: 'SignalIntermediateCatch',
        type: 'bpmn:IntermediateCatchEvent',
      },
      {
        id: 'SignalBoundaryEvent',
        type: 'bpmn:BoundaryEvent',
      },
      {
        id: 'SignalEventSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'SignalStartEvent',
        type: 'bpmn:StartEvent',
      },
      {
        id: 'MultiInstanceSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'EscalationEventSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'EscalationStartEvent',
        type: 'bpmn:StartEvent',
      },
      {
        id: 'CompensationBoundaryEvent',
        type: 'bpmn:BoundaryEvent',
      },
      {
        id: 'CompensationTask',
        type: 'bpmn:ServiceTask',
      },
    ]);

    expect(isAutoMappable('checkPayment')).toBe(true);
    expect(isAutoMappable('ExclusiveGateway')).toBe(true);
    expect(isAutoMappable('shipArticles')).toBe(true);
    expect(isAutoMappable('MessageInterrupting')).toBe(true);
    expect(isAutoMappable('TimerNonInterrupting')).toBe(true);
    expect(isAutoMappable('MessageIntermediateCatch')).toBe(true);
    expect(isAutoMappable('MessageEventSubProcess')).toBe(true);
    expect(isAutoMappable('TimerEventSubProcess')).toBe(true);
    expect(isAutoMappable('TaskX')).toBe(true);
    expect(isAutoMappable('BusinessRuleTask')).toBe(true);
    expect(isAutoMappable('ScriptTask')).toBe(true);
    expect(isAutoMappable('SendTask')).toBe(true);
    expect(isAutoMappable('EventBasedGateway')).toBe(true);
    expect(isAutoMappable('IntermediateTimerEvent')).toBe(true);
    expect(isAutoMappable('SignalIntermediateCatch')).toBe(true);
    expect(isAutoMappable('SignalBoundaryEvent')).toBe(true);
    expect(isAutoMappable('SignalEventSubProcess')).toBe(true);
    expect(isAutoMappable('SignalStartEvent')).toBe(true);
    expect(isAutoMappable('ErrorEventSubProcess')).toBe(true);
    expect(isAutoMappable('ErrorStartEvent')).toBe(true);
    expect(isAutoMappable('MultiInstanceSubProcess')).toBe(true);
    expect(isAutoMappable('ParallelGateway_1')).toBe(true);
    expect(isAutoMappable('ParallelGateway_2')).toBe(true);
    expect(isAutoMappable('ParallelGateway_3')).toBe(true);
    expect(isAutoMappable('ParallelGateway_4')).toBe(true);

    expect(isAutoMappable('requestForPayment')).toBe(false);
    expect(isAutoMappable('TimerInterrupting')).toBe(false);
    expect(isAutoMappable('MessageNonInterrupting')).toBe(false);
    expect(isAutoMappable('TimerIntermediateCatch')).toBe(false);
    expect(isAutoMappable('TaskY')).toBe(false);
    expect(isAutoMappable('TaskZ')).toBe(false);

    expect(isAutoMappable('unknownFlowNodeId')).toBe(false);
    expect(isAutoMappable('')).toBe(false);
  });

  it('should get mappable flow nodes', async () => {
    // Fetch migration source diagram
    mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));
    processXmlMigrationSourceStore.fetchProcessXml();
    await waitFor(() =>
      expect(processXmlMigrationSourceStore.state.status).toBe('fetched'),
    );

    // Fetch migration target diagram
    mockFetchProcessXML().withSuccess(open('instanceMigration_v2.bpmn'));
    processXmlMigrationTargetStore.fetchProcessXml();
    await waitFor(() =>
      expect(processXmlMigrationTargetStore.state.status).toBe('fetched'),
    );

    expect(processInstanceMigrationMappingStore.mappableFlowNodes).toEqual([
      {
        sourceFlowNode: {
          id: 'checkPayment',
          name: 'Check payment',
        },
        selectableTargetFlowNodes: [
          {
            id: 'checkPayment',
            name: 'Check payment',
          },
          {
            id: 'TaskX',
            name: 'Task X',
          },
          {
            id: 'TaskYY',
            name: 'Task YY',
          },
          {
            id: 'CompensationTask',
            name: 'Compensation task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ExclusiveGateway',
          name: 'Payment OK?',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ExclusiveGateway',
            name: 'Payment OK?',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'requestForPayment',
          name: 'Request for payment',
        },
        selectableTargetFlowNodes: [
          {
            id: 'checkPayment',
            name: 'Check payment',
          },
          {
            id: 'TaskX',
            name: 'Task X',
          },
          {
            id: 'TaskYY',
            name: 'Task YY',
          },
          {
            id: 'CompensationTask',
            name: 'Compensation task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'shippingSubProcess',
          name: 'Shipping Sub Process',
        },
        selectableTargetFlowNodes: [{id: 'SubProcess', name: 'Sub Process'}],
      },
      {
        sourceFlowNode: {
          id: 'shipArticles',
          name: 'Ship Articles',
        },
        selectableTargetFlowNodes: [
          {
            id: 'requestForPayment',
            name: 'Request for payment',
          },
          {
            id: 'shipArticles',
            name: 'Ship Articles',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ParallelGateway_1',
          name: 'ParallelGateway_1',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ParallelGateway_1',
            name: 'ParallelGateway_1',
          },
          {
            id: 'ParallelGateway_2',
            name: 'ParallelGateway_2',
          },
          {
            id: 'ParallelGateway_3',
            name: 'ParallelGateway_3',
          },
          {
            id: 'ParallelGateway_4',
            name: 'ParallelGateway_4',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ParallelGateway_2',
          name: 'ParallelGateway_2',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ParallelGateway_1',
            name: 'ParallelGateway_1',
          },
          {
            id: 'ParallelGateway_2',
            name: 'ParallelGateway_2',
          },
          {
            id: 'ParallelGateway_3',
            name: 'ParallelGateway_3',
          },
          {
            id: 'ParallelGateway_4',
            name: 'ParallelGateway_4',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MessageInterrupting',
          name: 'Message interrupting',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MessageInterrupting',
            name: 'Message interrupting',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TimerInterrupting',
          name: 'Timer interrupting',
        },
        selectableTargetFlowNodes: [
          {
            id: 'TimerNonInterrupting',
            name: 'Timer non-interrupting',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MessageNonInterrupting',
          name: 'Message non-interrupting',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MessageInterrupting',
            name: 'Message interrupting',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TimerNonInterrupting',
          name: 'Timer non-interrupting',
        },
        selectableTargetFlowNodes: [
          {
            id: 'TimerNonInterrupting',
            name: 'Timer non-interrupting',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'confirmDelivery',
          name: 'Confirm delivery',
        },
        selectableTargetFlowNodes: [],
      },
      {
        sourceFlowNode: {
          id: 'MessageIntermediateCatch',
          name: 'Message intermediate catch',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MessageIntermediateCatch',
            name: 'Message intermediate catch',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TimerIntermediateCatch',
          name: 'Timer intermediate catch',
        },
        selectableTargetFlowNodes: [
          {
            id: 'IntermediateTimerEvent',
            name: 'IntermediateTimerEvent',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MessageEventSubProcess',
          name: 'Message event sub process',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MessageEventSubProcess',
            name: 'Message event sub process',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TaskX',
          name: 'Task X',
        },
        selectableTargetFlowNodes: [
          {
            id: 'checkPayment',
            name: 'Check payment',
          },
          {
            id: 'TaskX',
            name: 'Task X',
          },
          {
            id: 'TaskYY',
            name: 'Task YY',
          },
          {
            id: 'CompensationTask',
            name: 'Compensation task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MessageStartEvent',
          name: 'Message start event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MessageStartEvent',
            name: 'Message start event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TimerEventSubProcess',
          name: 'Timer event sub process',
        },
        selectableTargetFlowNodes: [
          {
            id: 'TimerEventSubProcess',
            name: 'Timer event sub process',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TaskY',
          name: 'Task Y',
        },
        selectableTargetFlowNodes: [
          {
            id: 'checkPayment',
            name: 'Check payment',
          },
          {
            id: 'TaskX',
            name: 'Task X',
          },
          {
            id: 'TaskYY',
            name: 'Task YY',
          },
          {
            id: 'CompensationTask',
            name: 'Compensation task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'TimerStartEvent',
          name: 'Timer start event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'TimerStartEvent',
            name: 'Timer start event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ErrorEventSubProcess',
          name: 'Error event sub process',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ErrorEventSubProcess',
            name: 'Error event sub process',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ErrorStartEvent',
          name: 'Error start event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ErrorStartEvent',
            name: 'Error start event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MessageReceiveTask',
          name: 'Message receive task',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MessageReceiveTask',
            name: 'Message receive task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ParallelGateway_3',
          name: 'ParallelGateway_3',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ParallelGateway_1',
            name: 'ParallelGateway_1',
          },
          {
            id: 'ParallelGateway_2',
            name: 'ParallelGateway_2',
          },
          {
            id: 'ParallelGateway_3',
            name: 'ParallelGateway_3',
          },
          {
            id: 'ParallelGateway_4',
            name: 'ParallelGateway_4',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ParallelGateway_4',
          name: 'ParallelGateway_4',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ParallelGateway_1',
            name: 'ParallelGateway_1',
          },
          {
            id: 'ParallelGateway_2',
            name: 'ParallelGateway_2',
          },
          {
            id: 'ParallelGateway_3',
            name: 'ParallelGateway_3',
          },
          {
            id: 'ParallelGateway_4',
            name: 'ParallelGateway_4',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'BusinessRuleTask',
          name: 'Business rule task',
        },
        selectableTargetFlowNodes: [
          {
            id: 'BusinessRuleTask',
            name: 'Business rule task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'ScriptTask',
          name: 'Script task',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ScriptTask',
            name: 'Script task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'SendTask',
          name: 'Send task',
        },
        selectableTargetFlowNodes: [
          {
            id: 'SendTask',
            name: 'Send task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'EventBasedGateway',
          name: 'EventBasedGateway',
        },
        selectableTargetFlowNodes: [
          {
            id: 'EventBasedGateway',
            name: 'EventBasedGateway',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'IntermediateTimerEvent',
          name: 'IntermediateTimerEvent',
        },
        selectableTargetFlowNodes: [
          {
            id: 'IntermediateTimerEvent',
            name: 'IntermediateTimerEvent',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'SignalIntermediateCatch',
          name: 'Signal intermediate catch',
        },
        selectableTargetFlowNodes: [
          {
            id: 'SignalIntermediateCatch',
            name: 'Signal intermediate catch',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'SignalBoundaryEvent',
          name: 'Signal boundary event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'SignalBoundaryEvent',
            name: 'Signal boundary event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'SignalEventSubProcess',
          name: 'Signal event sub process',
        },
        selectableTargetFlowNodes: [
          {
            id: 'SignalEventSubProcess',
            name: 'Signal event sub process',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'SignalStartEvent',
          name: 'Signal start event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'SignalStartEvent',
            name: 'Signal start event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MultiInstanceSubProcess',
          name: 'Multi instance sub process',
        },
        selectableTargetFlowNodes: [
          {
            id: 'MultiInstanceSubProcess',
            name: 'Multi instance sub process',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'MultiInstanceTask',
          name: 'Multi instance task',
        },
        selectableTargetFlowNodes: [
          {
            id: 'ParallelTask',
            name: 'Parallel task',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'EscalationEventSubProcess',
          name: 'Escalation event sub process',
        },
        selectableTargetFlowNodes: [
          {
            id: 'EscalationEventSubProcess',
            name: 'Escalation event sub process',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'EscalationStartEvent',
          name: 'Escalation start event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'EscalationStartEvent',
            name: 'Escalation start event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'CompensationBoundaryEvent',
          name: 'Compensation boundary event',
        },
        selectableTargetFlowNodes: [
          {
            id: 'CompensationBoundaryEvent',
            name: 'Compensation boundary event',
          },
        ],
      },
      {
        sourceFlowNode: {
          id: 'CompensationTask',
          name: 'Compensation task',
        },
        selectableTargetFlowNodes: [
          {
            id: 'checkPayment',
            name: 'Check payment',
          },
          {
            id: 'TaskX',
            name: 'Task X',
          },
          {
            id: 'TaskYY',
            name: 'Task YY',
          },
          {
            id: 'CompensationTask',
            name: 'Compensation task',
          },
        ],
      },
    ]);
  });

  it('should toggle mapped filter', () => {
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(false);

    processInstanceMigrationMappingStore.toggleMappedFilter();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(true);

    processInstanceMigrationMappingStore.toggleMappedFilter();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(false);

    processInstanceMigrationMappingStore.toggleMappedFilter();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(true);

    processInstanceMigrationMappingStore.reset();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(false);
  });
});

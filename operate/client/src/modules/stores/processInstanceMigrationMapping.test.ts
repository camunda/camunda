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

/**
 * In these tests a migration mapping from orderProcess.bpmn to orderProcess_v2.bpmn is tested
 *
 * orderProcess.bpmn contains:
 * - checkPayment (service task)
 * - requestForPayment (service task)
 * - shipArticles (user task)
 * - MessageInterrupting (event)
 * - TimerInterrupting (event)
 * - MessageNonInterrupting (event)
 * - TimerNonInterrupting (event)
 * - MessageIntermediateCatch (event)
 * - TimerIntermediateCatch (event)
 * - MessageEventSubProcess
 * - TimerEventSubProcess
 * - ErrorEventSubProcess
 * - MessageStartEvent
 * - TimerStartEvent
 * - ErrorStartEvent
 * - MessageReceiveTask
 * - BusinessRuleTask
 * - SendTask
 * - ScriptTask
 *
 * orderProcess_v2.bpmn contains:
 * - checkPayment (service task)
 * - requestForPayment (user task)
 * - shipArticles (user task)
 * - MessageInterrupting (event)
 * - TimerNonInterrupting (event)
 * - MessageIntermediateCatch (event)
 * - MessageEventSubProcess (sub process)
 * - TimerEventSubProcess (sub process)
 * - ErrorEventSubProcess (sub process)
 * - MessageStartEvent
 * - TimerStartEvent
 * - ErrorStartEvent
 * - MessageReceiveTask
 * - BusinessRuleTask
 * - SendTask
 * - ScriptTask
 */
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
      {id: 'shipArticles', type: 'bpmn:UserTask'},
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
        id: 'TimerEventSubProcess',
        type: 'bpmn:SubProcess',
      },
      {
        id: 'MessageReceiveTask',
        type: 'bpmn:ReceiveTask',
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
    ]);

    expect(isAutoMappable('checkPayment')).toBe(true);
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

    expect(isAutoMappable('requestForPayment')).toBe(false);
    expect(isAutoMappable('TimerInterrupting')).toBe(false);
    expect(isAutoMappable('MessageNonInterrupting')).toBe(false);
    expect(isAutoMappable('TimerIntermediateCatch')).toBe(false);
    expect(isAutoMappable('ErrorEventSubProcess')).toBe(false);
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
        ],
      },
      {
        sourceFlowNode: {
          id: 'shippingSubProcess',
          name: 'Shipping Sub Process',
        },
        selectableTargetFlowNodes: [],
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
        selectableTargetFlowNodes: [],
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

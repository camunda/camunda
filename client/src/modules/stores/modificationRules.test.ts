/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {modificationsStore} from './modifications';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {modificationRulesStore} from './modificationRules';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {
  incidentFlowNodeMetaData,
  singleInstanceMetadata,
} from 'modules/mocks/metadata';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {open} from 'modules/mocks/diagrams';

describe('stores/modificationRules', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'service-task-1',
        active: 0,
        incidents: 0,
        completed: 2,
        canceled: 0,
      },
      {
        activityId: 'service-task-2',
        active: 2,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'multi-instance-subprocess',
        active: 2,
        incidents: 0,
        completed: 1,
        canceled: 0,
      },
      {
        activityId: 'eventAttachedToEventbasedGateway1',
        active: 0,
        incidents: 0,
        completed: 2,
        canceled: 0,
      },
      {
        activityId: 'eventAttachedToEventbasedGateway2',
        active: 2,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml('1');
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics('1');

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '1',
        state: 'ACTIVE',
        processName: 'processName',
        bpmnProcessId: 'processName',
      })
    );

    flowNodeMetaDataStore.setMetaData(incidentFlowNodeMetaData);
  });

  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStatisticsStore.reset();
    flowNodeMetaDataStore.reset();
  });

  it('should get modification rules for a non appendable flow node without running instances', () => {
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'eventAttachedToEventbasedGateway1',
    });

    expect(modificationRulesStore.canBeModified).toBe(false);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'eventAttachedToEventbasedGateway1',
      flowNodeInstanceId: 'some-instance-key',
    });

    expect(modificationRulesStore.canBeModified).toBe(false);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);
  });

  it('should get modification rules for a non appendable flow node with running instances', () => {
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'eventAttachedToEventbasedGateway2',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'cancel-all',
      'move-all',
    ]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'eventAttachedToEventbasedGateway2',
      flowNodeInstanceId: 'some-instance-key',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);

    expect(modificationRulesStore.availableModifications).toEqual([
      'cancel-instance',
      'move-instance',
    ]);
  });

  it('should get modification rules for a flow node with running instances', () => {
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'add',
      'cancel-all',
      'move-all',
    ]);

    // cancel all instances
    modificationsStore.cancelAllTokens('service-task-2');
    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual(['add']);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
      flowNodeInstanceId: 'some-instance-key-1',
    });
    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
      flowNodeInstanceId: 'some-instance-key-2',
    });
    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    modificationsStore.removeLastModification();
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
    });

    // cancel one of the instances
    modificationsStore.cancelToken('service-task-2', 'some-instance-key-1');
    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'add',
      'cancel-all',
      'move-all',
    ]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
      flowNodeInstanceId: 'some-instance-key-1',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
      flowNodeInstanceId: 'some-instance-key-2',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'cancel-instance',
      'move-instance',
    ]);

    // cancel the other instance
    modificationsStore.cancelToken('service-task-2', 'some-instance-key-2');
    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
    });
    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual(['add']);
  });

  it('should get modification rules for a flow node without running instances', () => {
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual(['add']);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: 'some-instance-key-1',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: 'some-instance-key-2',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual(['add']);
  });

  it('should get modification rules for multi instance subprocess and its instances', () => {
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'multi-instance-subprocess',
      isMultiInstance: true,
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'add',
      'cancel-all',
    ]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'multi-instance-subprocess',
      flowNodeInstanceId: 'some-instance-key-1',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'cancel-all',
    ]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'multi-instance-subprocess',
      flowNodeInstanceId: 'some-instance-key-2',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(true);
    expect(modificationRulesStore.availableModifications).toEqual([
      'cancel-all',
    ]);

    flowNodeMetaDataStore.setMetaData(singleInstanceMetadata);
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'multi-instance-subprocess',
      flowNodeInstanceId: 'some-finished-instance-key',
    });

    expect(modificationRulesStore.canBeModified).toBe(true);
    expect(modificationRulesStore.canBeCanceled).toBe(false);
    expect(modificationRulesStore.availableModifications).toEqual([]);
  });

  it('should display/hide add token option depending on flow node/instance key selection', () => {
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
    });

    expect(modificationRulesStore.availableModifications).toEqual([
      'add',
      'cancel-all',
      'move-all',
    ]);
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-2',
      flowNodeInstanceId: 'some-instance-key',
    });

    expect(modificationRulesStore.availableModifications).toEqual([
      'cancel-instance',
      'move-instance',
    ]);
  });
});

/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      }),
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

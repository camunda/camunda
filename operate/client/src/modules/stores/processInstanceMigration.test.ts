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

import {operationsStore} from './operations';
import {processInstanceMigrationStore} from './processInstanceMigration';

const SOURCE_TASK_A = 'sourceTaskA';
const TARGET_TASK_A = 'targetTaskA';
const SOURCE_TASK_B = 'sourceTaskB';

jest
  .spyOn(operationsStore, 'applyBatchOperation')
  .mockImplementation(jest.fn());

describe('processInstanceMigration', () => {
  afterEach(() => {
    processInstanceMigrationStore.reset();
  });

  it('should set and get steps', () => {
    processInstanceMigrationStore.enable();

    expect(processInstanceMigrationStore.isSummaryStep).toBe(false);
    expect(processInstanceMigrationStore.currentStep).toEqual({
      stepDescription: 'Mapping elements',
      stepNumber: 1,
    });
    expect(processInstanceMigrationStore.isSummaryStep).toBe(false);

    processInstanceMigrationStore.setCurrentStep('summary');

    expect(processInstanceMigrationStore.currentStep).toEqual({
      stepDescription: 'Confirm',
      stepNumber: 2,
    });
    expect(processInstanceMigrationStore.isSummaryStep).toBe(true);

    processInstanceMigrationStore.disable();

    expect(processInstanceMigrationStore.isSummaryStep).toBe(false);
    expect(processInstanceMigrationStore.currentStep).toBeNull();
  });

  it('should update flow node mapping', () => {
    processInstanceMigrationStore.enable();

    expect(processInstanceMigrationStore.hasFlowNodeMapping).toBe(false);
    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({});

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });

    expect(processInstanceMigrationStore.hasFlowNodeMapping).toBe(true);
    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({
      startEvent: 'endEvent',
    });

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({
      startEvent: 'endEvent',
      taskA: 'taskB',
    });

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'startEvent',
      targetId: '',
    });

    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({
      taskA: 'taskB',
    });

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'taskA',
      targetId: '',
    });

    expect(processInstanceMigrationStore.hasFlowNodeMapping).toBe(false);
    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({});

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({
      taskA: 'taskB',
    });

    processInstanceMigrationStore.resetFlowNodeMapping();

    expect(processInstanceMigrationStore.hasFlowNodeMapping).toBe(false);
    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({});
  });

  it('should request batch process after confirm migration', async () => {
    processInstanceMigrationStore.enable();

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });
    processInstanceMigrationStore.setBatchOperationQuery({
      active: true,
      incidents: false,
      ids: [],
      excludeIds: [],
    });
    processInstanceMigrationStore.setTargetProcessDefinitionKey(
      'targetProcessDefinitionKey',
    );
    processInstanceMigrationStore.setHasPendingRequest();
    operationsStore.handleFetchSuccess();

    expect(operationsStore.applyBatchOperation).toHaveBeenCalledWith(
      expect.objectContaining({
        migrationPlan: {
          mappingInstructions: [
            {sourceElementId: 'startEvent', targetElementId: 'endEvent'},
          ],
          targetProcessDefinitionKey: 'targetProcessDefinitionKey',
        },
        query: {
          active: true,
          incidents: false,
          ids: [],
          excludeIds: [],
        },
      }),
    );
  });

  it('should select flow nodes on source flow node selection', () => {
    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: SOURCE_TASK_A,
      targetId: TARGET_TASK_A,
    });

    processInstanceMigrationStore.selectSourceFlowNode(SOURCE_TASK_A);

    expect(processInstanceMigrationStore.selectedSourceFlowNodeIds).toEqual([
      SOURCE_TASK_A,
    ]);
    expect(processInstanceMigrationStore.selectedTargetFlowNodeId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: SOURCE_TASK_B,
      targetId: TARGET_TASK_A,
    });

    expect(processInstanceMigrationStore.selectedSourceFlowNodeIds).toEqual([
      SOURCE_TASK_A,
      SOURCE_TASK_B,
    ]);
    expect(processInstanceMigrationStore.selectedTargetFlowNodeId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.selectSourceFlowNode();

    expect(
      processInstanceMigrationStore.selectedSourceFlowNodeIds,
    ).toBeUndefined();
    expect(
      processInstanceMigrationStore.selectedTargetFlowNodeId,
    ).toBeUndefined();
  });

  it('should select flow nodes on target flow node selection', () => {
    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: SOURCE_TASK_A,
      targetId: TARGET_TASK_A,
    });

    processInstanceMigrationStore.selectTargetFlowNode(TARGET_TASK_A);

    expect(processInstanceMigrationStore.selectedSourceFlowNodeIds).toEqual([
      SOURCE_TASK_A,
    ]);
    expect(processInstanceMigrationStore.selectedTargetFlowNodeId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: SOURCE_TASK_B,
      targetId: TARGET_TASK_A,
    });

    expect(processInstanceMigrationStore.selectedSourceFlowNodeIds).toEqual([
      SOURCE_TASK_A,
      SOURCE_TASK_B,
    ]);
    expect(processInstanceMigrationStore.selectedTargetFlowNodeId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.selectTargetFlowNode();

    expect(
      processInstanceMigrationStore.selectedSourceFlowNodeIds,
    ).toBeUndefined();
    expect(
      processInstanceMigrationStore.selectedTargetFlowNodeId,
    ).toBeUndefined();
  });
});

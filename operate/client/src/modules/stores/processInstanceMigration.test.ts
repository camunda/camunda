/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      stepDescription: 'mapping elements',
      stepNumber: 1,
    });
    expect(processInstanceMigrationStore.isSummaryStep).toBe(false);

    processInstanceMigrationStore.setCurrentStep('summary');

    expect(processInstanceMigrationStore.currentStep).toEqual({
      stepDescription: 'confirm',
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

  it('should clear flow node mapping', () => {
    processInstanceMigrationStore.enable();

    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({});

    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });
    processInstanceMigrationStore.updateFlowNodeMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.flowNodeMapping).toEqual({
      startEvent: 'endEvent',
      taskA: 'taskB',
    });

    processInstanceMigrationStore.clearFlowNodeMapping();

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

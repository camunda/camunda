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

    expect(processInstanceMigrationStore.hasItemMapping).toBe(false);
    expect(processInstanceMigrationStore.state.itemMapping).toEqual({});

    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });

    expect(processInstanceMigrationStore.hasItemMapping).toBe(true);
    expect(processInstanceMigrationStore.state.itemMapping).toEqual({
      startEvent: 'endEvent',
    });

    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.itemMapping).toEqual({
      startEvent: 'endEvent',
      taskA: 'taskB',
    });

    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'startEvent',
      targetId: '',
    });

    expect(processInstanceMigrationStore.state.itemMapping).toEqual({
      taskA: 'taskB',
    });

    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'taskA',
      targetId: '',
    });

    expect(processInstanceMigrationStore.hasItemMapping).toBe(false);
    expect(processInstanceMigrationStore.state.itemMapping).toEqual({});

    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.itemMapping).toEqual({
      taskA: 'taskB',
    });

    processInstanceMigrationStore.resetItemMapping();

    expect(processInstanceMigrationStore.hasItemMapping).toBe(false);
    expect(processInstanceMigrationStore.state.itemMapping).toEqual({});
  });

  it('should clear flow node mapping', () => {
    processInstanceMigrationStore.enable();

    expect(processInstanceMigrationStore.state.itemMapping).toEqual({});

    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });
    processInstanceMigrationStore.updateItemMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.itemMapping).toEqual({
      startEvent: 'endEvent',
      taskA: 'taskB',
    });

    processInstanceMigrationStore.clearItemMapping();

    expect(processInstanceMigrationStore.state.itemMapping).toEqual({});
  });

  it('should request batch process after confirm migration', async () => {
    vi.spyOn(operationsStore, 'applyBatchOperation').mockImplementation(
      vi.fn(),
    );

    processInstanceMigrationStore.enable();

    processInstanceMigrationStore.updateItemMapping({
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
    processInstanceMigrationStore.updateItemMapping({
      sourceId: SOURCE_TASK_A,
      targetId: TARGET_TASK_A,
    });

    processInstanceMigrationStore.selectSourceFlowNode(SOURCE_TASK_A);

    expect(processInstanceMigrationStore.selectedSourceItemIds).toEqual([
      SOURCE_TASK_A,
    ]);
    expect(processInstanceMigrationStore.selectedTargetItemId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.updateItemMapping({
      sourceId: SOURCE_TASK_B,
      targetId: TARGET_TASK_A,
    });

    expect(processInstanceMigrationStore.selectedSourceItemIds).toEqual([
      SOURCE_TASK_A,
      SOURCE_TASK_B,
    ]);
    expect(processInstanceMigrationStore.selectedTargetItemId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.selectSourceFlowNode();

    expect(processInstanceMigrationStore.selectedSourceItemIds).toBeUndefined();
    expect(processInstanceMigrationStore.selectedTargetItemId).toBeUndefined();
  });

  it('should select flow nodes on target flow node selection', () => {
    processInstanceMigrationStore.updateItemMapping({
      sourceId: SOURCE_TASK_A,
      targetId: TARGET_TASK_A,
    });

    processInstanceMigrationStore.selectTargetItem(TARGET_TASK_A);

    expect(processInstanceMigrationStore.selectedSourceItemIds).toEqual([
      SOURCE_TASK_A,
    ]);
    expect(processInstanceMigrationStore.selectedTargetItemId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.updateItemMapping({
      sourceId: SOURCE_TASK_B,
      targetId: TARGET_TASK_A,
    });

    expect(processInstanceMigrationStore.selectedSourceItemIds).toEqual([
      SOURCE_TASK_A,
      SOURCE_TASK_B,
    ]);
    expect(processInstanceMigrationStore.selectedTargetItemId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.selectTargetItem();

    expect(processInstanceMigrationStore.selectedSourceItemIds).toBeUndefined();
    expect(processInstanceMigrationStore.selectedTargetItemId).toBeUndefined();
  });
});

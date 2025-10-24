/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

    expect(processInstanceMigrationStore.hasElementMapping).toBe(false);
    expect(processInstanceMigrationStore.state.elementMapping).toEqual({});

    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });

    expect(processInstanceMigrationStore.hasElementMapping).toBe(true);
    expect(processInstanceMigrationStore.state.elementMapping).toEqual({
      startEvent: 'endEvent',
    });

    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.elementMapping).toEqual({
      startEvent: 'endEvent',
      taskA: 'taskB',
    });

    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'startEvent',
      targetId: '',
    });

    expect(processInstanceMigrationStore.state.elementMapping).toEqual({
      taskA: 'taskB',
    });

    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'taskA',
      targetId: '',
    });

    expect(processInstanceMigrationStore.hasElementMapping).toBe(false);
    expect(processInstanceMigrationStore.state.elementMapping).toEqual({});

    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.elementMapping).toEqual({
      taskA: 'taskB',
    });

    processInstanceMigrationStore.resetElementMapping();

    expect(processInstanceMigrationStore.hasElementMapping).toBe(false);
    expect(processInstanceMigrationStore.state.elementMapping).toEqual({});
  });

  it('should clear flow node mapping', () => {
    processInstanceMigrationStore.enable();

    expect(processInstanceMigrationStore.state.elementMapping).toEqual({});

    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'startEvent',
      targetId: 'endEvent',
    });
    processInstanceMigrationStore.updateElementMapping({
      sourceId: 'taskA',
      targetId: 'taskB',
    });

    expect(processInstanceMigrationStore.state.elementMapping).toEqual({
      startEvent: 'endEvent',
      taskA: 'taskB',
    });

    processInstanceMigrationStore.clearElementMapping();

    expect(processInstanceMigrationStore.state.elementMapping).toEqual({});
  });

  it('should select flow nodes on source flow node selection', () => {
    processInstanceMigrationStore.updateElementMapping({
      sourceId: SOURCE_TASK_A,
      targetId: TARGET_TASK_A,
    });

    processInstanceMigrationStore.selectSourceFlowNode(SOURCE_TASK_A);

    expect(processInstanceMigrationStore.selectedSourceElementIds).toEqual([
      SOURCE_TASK_A,
    ]);
    expect(processInstanceMigrationStore.selectedTargetElementId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.updateElementMapping({
      sourceId: SOURCE_TASK_B,
      targetId: TARGET_TASK_A,
    });

    expect(processInstanceMigrationStore.selectedSourceElementIds).toEqual([
      SOURCE_TASK_A,
      SOURCE_TASK_B,
    ]);
    expect(processInstanceMigrationStore.selectedTargetElementId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.selectSourceFlowNode();

    expect(
      processInstanceMigrationStore.selectedSourceElementIds,
    ).toBeUndefined();
    expect(
      processInstanceMigrationStore.selectedTargetElementId,
    ).toBeUndefined();
  });

  it('should select flow nodes on target flow node selection', () => {
    processInstanceMigrationStore.updateElementMapping({
      sourceId: SOURCE_TASK_A,
      targetId: TARGET_TASK_A,
    });

    processInstanceMigrationStore.selectTargetElement(TARGET_TASK_A);

    expect(processInstanceMigrationStore.selectedSourceElementIds).toEqual([
      SOURCE_TASK_A,
    ]);
    expect(processInstanceMigrationStore.selectedTargetElementId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.updateElementMapping({
      sourceId: SOURCE_TASK_B,
      targetId: TARGET_TASK_A,
    });

    expect(processInstanceMigrationStore.selectedSourceElementIds).toEqual([
      SOURCE_TASK_A,
      SOURCE_TASK_B,
    ]);
    expect(processInstanceMigrationStore.selectedTargetElementId).toBe(
      TARGET_TASK_A,
    );

    processInstanceMigrationStore.selectTargetElement();

    expect(
      processInstanceMigrationStore.selectedSourceElementIds,
    ).toBeUndefined();
    expect(
      processInstanceMigrationStore.selectedTargetElementId,
    ).toBeUndefined();
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceMigrationStore} from './processInstanceMigration';

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
});

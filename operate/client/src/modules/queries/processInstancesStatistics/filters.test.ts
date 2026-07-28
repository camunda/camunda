/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {getSelectedProcessInstancesFilter} from './filters';

describe('getSelectedProcessInstancesFilter', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalProcessInstancesCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1', '2'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return undefined when nothing is selected', () => {
    expect(getSelectedProcessInstancesFilter()).toBeUndefined();
  });

  it('should return $in with the selected ids in INCLUDE mode', () => {
    processInstancesSelectionStore.selectProcessInstance('1');
    processInstancesSelectionStore.selectProcessInstance('3');

    expect(getSelectedProcessInstancesFilter()).toEqual({$in: ['1', '3']});
  });

  it('should return $notIn with the excluded ids in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAllProcessInstances();
    processInstancesSelectionStore.selectProcessInstance('2');

    expect(getSelectedProcessInstancesFilter()).toEqual({$notIn: ['2']});
  });

  it('should return undefined in ALL mode', () => {
    processInstancesSelectionStore.selectAllProcessInstances();

    expect(getSelectedProcessInstancesFilter()).toBeUndefined();
  });
});

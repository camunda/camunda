/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from './instancesSelection';

describe('InstancesSelection - checkedIds', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1', '3', '4'],
      visibleFinishedIds: ['2'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return selectedIds when selectionMode is INCLUDE', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.checkedIds).toEqual(['1', '3']);
  });

  it('should return visible ids minus excluded when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.checkedIds).toEqual(['2', '4']);
  });

  it('should return all visible ids when selectionMode is ALL', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.checkedIds).toEqual([
      '1',
      '2',
      '3',
      '4',
    ]);
  });

  it('should return empty array when visibleIds is empty and selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 0,
      visibleIds: [],
      visibleRunningIds: [],
      visibleFinishedIds: [],
    });
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.checkedIds).toEqual([]);
  });

  it('should return empty array when selectionMode is INCLUDE and nothing is selected', () => {
    expect(processInstancesSelectionStore.checkedIds).toEqual([]);
  });
});

describe('InstancesSelection - hasSelectedFinishedInstances', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 5,
      visibleIds: ['1', '2', '3', '4', '5'],
      visibleRunningIds: ['1', '3', '4'],
      visibleFinishedIds: ['2', '5'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return true when finished instances are selected in INCLUDE mode', () => {
    processInstancesSelectionStore.select('2'); // finished
    processInstancesSelectionStore.select('3'); // running

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      true,
    );
  });

  it('should return false when no finished instances are selected in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1'); // running
    processInstancesSelectionStore.select('3'); // running
    processInstancesSelectionStore.select('4'); // running

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      false,
    );
  });

  it('should return true when selectionMode is ALL', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      true,
    );
  });

  it('should return true when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      true,
    );
  });
});

describe('InstancesSelection - isChecked', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return true for ids in selectedIds when INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.isChecked('1')).toBe(true);
    expect(processInstancesSelectionStore.isChecked('2')).toBe(false);
    expect(processInstancesSelectionStore.isChecked('3')).toBe(true);
  });

  it('should return false for ids in excludedIds when EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('2');

    expect(processInstancesSelectionStore.isChecked('1')).toBe(true);
    expect(processInstancesSelectionStore.isChecked('2')).toBe(false);
    expect(processInstancesSelectionStore.isChecked('3')).toBe(true);
  });

  it('should return true for all ids when ALL mode', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.isChecked('1')).toBe(true);
    expect(processInstancesSelectionStore.isChecked('99')).toBe(true);
  });
});

describe('InstancesSelection - selectAll', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should switch to ALL mode when nothing is selected', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.state.selectionMode).toBe('ALL');
  });

  it('should deselect all when some items are selected in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('2');

    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.state.selectionMode).toBe('INCLUDE');
    expect(processInstancesSelectionStore.state.selectedIds).toEqual([]);
  });

  it('should deselect all when in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1'); // → EXCLUDE with ['1']

    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.state.selectionMode).toBe('INCLUDE');
    expect(processInstancesSelectionStore.state.selectedIds).toEqual([]);
  });
});

describe('InstancesSelection - select', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should add an id to selectedIds in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');

    expect(processInstancesSelectionStore.state.selectedIds).toEqual(['1']);
    expect(processInstancesSelectionStore.state.selectionMode).toBe('INCLUDE');
  });

  it('should remove an id from selectedIds when already selected in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('2');

    processInstancesSelectionStore.select('1'); // deselect

    expect(processInstancesSelectionStore.state.selectedIds).toEqual(['2']);
  });

  it('should switch from ALL to EXCLUDE mode and add id to excludedIds', () => {
    processInstancesSelectionStore.selectAll();

    processInstancesSelectionStore.select('2');

    expect(processInstancesSelectionStore.state.selectionMode).toBe('EXCLUDE');
    expect(processInstancesSelectionStore.state.selectedIds).toEqual(['2']);
  });

  it('should remove id from excludedIds in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1'); // exclude 1
    processInstancesSelectionStore.select('2'); // exclude 2

    processInstancesSelectionStore.select('1'); // re-include 1

    expect(processInstancesSelectionStore.state.selectionMode).toBe('EXCLUDE');
    expect(processInstancesSelectionStore.state.selectedIds).toEqual(['2']);
  });
});

describe('InstancesSelection - selectedCount', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 5,
      visibleIds: ['1', '2', '3', '4', '5'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return the number of selectedIds in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.selectedCount).toBe(2);
  });

  it('should return totalCount minus excludedIds count in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1'); // exclude 1
    processInstancesSelectionStore.select('2'); // exclude 2

    expect(processInstancesSelectionStore.selectedCount).toBe(3);
  });

  it('should return totalCount in ALL mode', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.selectedCount).toBe(5);
  });
});

describe('InstancesSelection - selectedIds getter', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return selectedIds in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.selectedIds).toEqual(['1', '3']);
  });

  it('should return empty array in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');

    expect(processInstancesSelectionStore.selectedIds).toEqual([]);
  });

  it('should return empty array in ALL mode', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.selectedIds).toEqual([]);
  });
});

describe('InstancesSelection - isAllChecked', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return true when selectionMode is ALL', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.isAllChecked).toBe(true);
  });

  it('should return false when selectionMode is INCLUDE', () => {
    processInstancesSelectionStore.select('1');

    expect(processInstancesSelectionStore.isAllChecked).toBe(false);
  });

  it('should return false when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');

    expect(processInstancesSelectionStore.isAllChecked).toBe(false);
  });
});

describe('InstancesSelection - hasSelectedRunningInstances', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1', '3'],
      visibleFinishedIds: ['2', '4'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return true when a running instance is selected in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1'); // running
    processInstancesSelectionStore.select('2'); // finished

    expect(processInstancesSelectionStore.hasSelectedRunningInstances).toBe(
      true,
    );
  });

  it('should return false when only finished instances are selected in INCLUDE mode', () => {
    processInstancesSelectionStore.select('2'); // finished
    processInstancesSelectionStore.select('4'); // finished

    expect(processInstancesSelectionStore.hasSelectedRunningInstances).toBe(
      false,
    );
  });

  it('should return true when selectionMode is ALL', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.hasSelectedRunningInstances).toBe(
      true,
    );
  });

  it('should return true when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    expect(processInstancesSelectionStore.hasSelectedRunningInstances).toBe(
      true,
    );
  });
});

describe('InstancesSelection - checkedRunningIds', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1', '3', '4'],
      visibleFinishedIds: ['2'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return only running ids from selectedIds in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1'); // running
    processInstancesSelectionStore.select('2'); // finished
    processInstancesSelectionStore.select('3'); // running

    expect(processInstancesSelectionStore.checkedRunningIds).toEqual([
      '1',
      '3',
    ]);
  });

  it('should return running ids not in excludedIds in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1'); // exclude running id

    expect(processInstancesSelectionStore.checkedRunningIds).toEqual([
      '3',
      '4',
    ]);
  });

  it('should return all running ids in ALL mode', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.checkedRunningIds).toEqual([
      '1',
      '3',
      '4',
    ]);
  });
});

describe('InstancesSelection - excludedIds', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return excluded ids when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('2');

    expect(processInstancesSelectionStore.excludedIds).toEqual(['1', '2']);
  });

  it('should return empty array when selectionMode is INCLUDE', () => {
    processInstancesSelectionStore.select('1');

    expect(processInstancesSelectionStore.excludedIds).toEqual([]);
  });

  it('should return empty array when selectionMode is ALL', () => {
    processInstancesSelectionStore.selectAll();

    expect(processInstancesSelectionStore.excludedIds).toEqual([]);
  });
});

describe('InstancesSelection - resetState', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['1', '2', '3'],
    });
  });

  it('should reset state to INCLUDE mode with empty selectedIds', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('1'); // → EXCLUDE with ['1']

    processInstancesSelectionStore.resetState();

    expect(processInstancesSelectionStore.state.selectedIds).toEqual([]);
    expect(processInstancesSelectionStore.state.selectionMode).toBe('INCLUDE');
  });
});

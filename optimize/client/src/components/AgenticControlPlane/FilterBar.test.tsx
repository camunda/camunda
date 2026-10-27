/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow, ShallowWrapper} from 'enzyme';
import {ComboBox, MenuItemSelectable} from '@carbon/react';

import {MenuDropdown} from 'components';
import {loadDefinitions, loadVersions} from 'services';

import {FilterBar, DATE_PRESETS} from './FilterBar';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadDefinitions: jest.fn().mockResolvedValue([
    {key: 'process-a', name: 'Process A'},
    {key: 'process-b', name: 'Process B'},
  ]),
  loadVersions: jest.fn().mockResolvedValue([
    {version: '3', versionTag: null},
    {version: '2', versionTag: 'v2-tag'},
    {version: '1', versionTag: null},
  ]),
}));

const props = {
  preset: '30d',
  onPresetChange: jest.fn(),
  processScope: null,
  onProcessScopeChange: jest.fn(),
  versions: ['all'],
  onVersionsChange: jest.fn(),
};

function dateRangeDropdown(node: ShallowWrapper) {
  return node.find('.dateRange').find(MenuDropdown);
}

function dateRangeItems(node: ShallowWrapper) {
  return node.find('.dateRange').find(MenuItemSelectable);
}

function versionDropdown(node: ShallowWrapper) {
  return node.find('.versionScope').find(MenuDropdown);
}

function versionItems(node: ShallowWrapper) {
  return node.find('.versionScope').find(MenuItemSelectable);
}

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render one option for each date preset', () => {
  const node = shallow(<FilterBar {...props} />);

  expect(dateRangeItems(node)).toHaveLength(DATE_PRESETS.length);
});

it('should mark only the active preset as selected', () => {
  const node = shallow(<FilterBar {...props} preset="7d" />);

  const selectedItems = dateRangeItems(node).filterWhere((n) => n.prop('selected'));
  expect(selectedItems).toHaveLength(1);
  expect(selectedItems.prop('label')).toBe('Last 7 days');
});

it('should show the active preset label in the dropdown trigger', () => {
  const node = shallow(<FilterBar {...props} preset="3m" />);

  expect(dateRangeDropdown(node).prop('label')).toBe('Last 3 months');
});

it('should call onPresetChange with the preset id when an option is selected', () => {
  const node = shallow(<FilterBar {...props} />);

  (dateRangeItems(node).at(0).prop('onChange') as () => void)();

  expect(props.onPresetChange).toHaveBeenCalledWith('7d');
});

it('should default to Last 30 days being selected when preset is 30d', () => {
  const node = shallow(<FilterBar {...props} preset="30d" />);

  const selectedItem = dateRangeItems(node).filterWhere((n) => n.prop('selected'));
  expect(selectedItem.prop('label')).toBe('Last 30 days');
});

it('should render a process scope ComboBox', () => {
  const node = shallow(<FilterBar {...props} />);

  expect(node.find(ComboBox)).toExist();
});

it('should load agent-enabled process definitions on mount', async () => {
  shallow(<FilterBar {...props} />);

  await runAllEffects();

  expect(loadDefinitions).toHaveBeenCalledWith('process', null);
});

it('should call onProcessScopeChange with the definition key when a process is selected', async () => {
  const node = shallow(<FilterBar {...props} />);

  await runAllEffects();

  (
    node.find(ComboBox).prop('onChange') as (e: {
      selectedItem: {key: string; name: string} | null;
    }) => void
  )({selectedItem: {key: 'process-a', name: 'Process A'}});

  expect(props.onProcessScopeChange).toHaveBeenCalledWith('process-a');
});

it('should call onProcessScopeChange with null when the ComboBox is cleared', async () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" />);

  await runAllEffects();

  (node.find(ComboBox).prop('onChange') as (e: {selectedItem: null}) => void)({selectedItem: null});

  expect(props.onProcessScopeChange).toHaveBeenCalledWith(null);
});

it('should show null selectedItem in ComboBox when no process is selected', () => {
  const node = shallow(<FilterBar {...props} processScope={null} />);

  expect(node.find(ComboBox).prop('selectedItem')).toBeNull();
});

it('should disable the version dropdown when no process is selected', () => {
  const node = shallow(<FilterBar {...props} processScope={null} />);

  expect(versionDropdown(node).prop('disabled')).toBe(true);
});

it('should enable the version dropdown when a process is selected', () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" />);

  expect(versionDropdown(node).prop('disabled')).toBe(false);
});

it('should not load versions when no process is selected', async () => {
  shallow(<FilterBar {...props} processScope={null} />);

  await runAllEffects();

  expect(loadVersions).not.toHaveBeenCalled();
});

it('should load versions for the selected process', async () => {
  shallow(<FilterBar {...props} processScope="process-a" />);

  await runAllEffects();

  expect(loadVersions).toHaveBeenCalledWith('process', null, 'process-a');
});

it('should render All, Latest and one option per loaded version', async () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" />);

  await runAllEffects();

  // All + Latest + 3 concrete versions
  expect(versionItems(node)).toHaveLength(5);
});

it('should mark All as selected by default', () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" versions={['all']} />);

  const selected = versionItems(node).filterWhere((n) => n.prop('selected'));
  expect(selected).toHaveLength(1);
  expect(selected.prop('label')).toBe('All versions');
});

it('should call onVersionsChange with [all] when All is selected', () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" versions={['latest']} />);

  (versionItems(node).at(0).prop('onChange') as () => void)();

  expect(props.onVersionsChange).toHaveBeenCalledWith(['all']);
});

it('should call onVersionsChange with [latest] when Latest is selected', () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" />);

  (versionItems(node).at(1).prop('onChange') as () => void)();

  expect(props.onVersionsChange).toHaveBeenCalledWith(['latest']);
});

it('should call onVersionsChange with a specific version when selected', async () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" />);

  await runAllEffects();

  // index 2 is the first concrete version ('3')
  (versionItems(node).at(2).prop('onChange') as () => void)();

  expect(props.onVersionsChange).toHaveBeenCalledWith(['3']);
});

it('should mark the specific selected version as selected', async () => {
  const node = shallow(<FilterBar {...props} processScope="process-a" versions={['2']} />);

  await runAllEffects();

  const selected = versionItems(node).filterWhere((n) => n.prop('selected'));
  expect(selected).toHaveLength(1);
  expect(selected.prop('label')).toBe('Version 2 (v2-tag)');
});

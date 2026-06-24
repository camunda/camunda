/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {ComboBox, MenuItemSelectable} from '@carbon/react';

import {MenuDropdown} from 'components';
import {loadDefinitions} from 'services';

import {FilterBar, DATE_PRESETS} from './FilterBar';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadDefinitions: jest.fn().mockResolvedValue([
    {key: 'process-a', name: 'Process A'},
    {key: 'process-b', name: 'Process B'},
  ]),
}));

const props = {
  preset: '30d',
  onPresetChange: jest.fn(),
  processScope: null,
  onProcessScopeChange: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render one option for each date preset', () => {
  const node = shallow(<FilterBar {...props} />);

  expect(node.find(MenuItemSelectable)).toHaveLength(DATE_PRESETS.length);
});

it('should mark only the active preset as selected', () => {
  const node = shallow(<FilterBar {...props} preset="7d" />);

  const selectedItems = node.find(MenuItemSelectable).filterWhere((n) => n.prop('selected'));
  expect(selectedItems).toHaveLength(1);
  expect(selectedItems.prop('label')).toBe('Last 7 days');
});

it('should show the active preset label in the dropdown trigger', () => {
  const node = shallow(<FilterBar {...props} preset="3m" />);

  expect(node.find(MenuDropdown).prop('label')).toBe('Last 3 months');
});

it('should call onPresetChange with the preset id when an option is selected', () => {
  const node = shallow(<FilterBar {...props} />);

  (node.find(MenuItemSelectable).at(0).prop('onChange') as () => void)();

  expect(props.onPresetChange).toHaveBeenCalledWith('7d');
});

it('should default to Last 30 days being selected when preset is 30d', () => {
  const node = shallow(<FilterBar {...props} preset="30d" />);

  const selectedItem = node.find(MenuItemSelectable).filterWhere((n) => n.prop('selected'));
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

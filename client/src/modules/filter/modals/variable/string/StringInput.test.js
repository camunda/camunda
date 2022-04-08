/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button, Input} from 'components';

import StringInput from './StringInput';

jest.mock('debouncePromise', () => () => (fn) => fn());

const props = {
  processDefinitionKey: 'procDefKey',
  processDefinitionVersion: '1',
  variable: {name: 'foo', type: 'String'},
  filter: StringInput.defaultFilter,
  config: {getValues: jest.fn().mockReturnValue(['val1', 'val2'])},
  setValid: jest.fn(),
  changeFilter: jest.fn(),
  definition: {identifier: 'definition'},
};

beforeEach(() => {
  props.changeFilter.mockClear();
  props.setValid.mockClear();
});

it('should show a checklist', () => {
  const node = shallow(<StringInput {...props} />);

  expect(node.find('Checklist')).toExist();
});

it('should load 10 values initially', () => {
  shallow(<StringInput {...props} />);

  expect(props.config.getValues).toHaveBeenCalledWith('foo', 'String', 11, '', props.definition);
});

it('should pass available values to the typeahead', () => {
  const availableValues = ['value1', 'value2', 'value3'];
  const node = shallow(<StringInput {...props} />);
  node.setState({availableValues});

  expect(node.find('Checklist').props().allItems).toEqual(availableValues);
});

it('should load 10 more values if the user wants more', () => {
  const node = shallow(<StringInput {...props} />);
  node.setState({
    availableValues: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
    valuesAreComplete: false,
    valuesLoaded: 10,
    loading: false,
  });

  node.find('[link]').simulate('click', {preventDefault: jest.fn()});

  expect(props.config.getValues).toHaveBeenCalledWith('foo', 'String', 21, '', props.definition);
});

it('should disable add filter button if no value is selected', () => {
  const changeSpy = jest.fn();
  const validSpy = jest.fn();
  const node = shallow(
    <StringInput
      {...props}
      filter={{operator: 'in', values: ['A']}}
      changeFilter={changeSpy}
      setValid={validSpy}
    />
  );

  node.find('Checklist').prop('onChange')([]);

  expect(changeSpy).toHaveBeenCalledWith({operator: 'in', values: []});
  expect(validSpy).toHaveBeenCalledWith(false);
});

it('should reset values when switching between operators types', () => {
  const node = shallow(<StringInput {...props} filter={{operator: 'in', values: ['A']}} />);

  node
    .find('.buttonRow')
    .find(Button)
    .at(2)
    .simulate('click', {preventDefault: () => {}});

  const newFilter = {operator: 'contains', values: []};
  expect(props.changeFilter).toHaveBeenCalledWith(newFilter);

  node.setProps({filter: newFilter});
  node
    .find('.buttonRow')
    .find(Button)
    .at(1)
    .simulate('click', {preventDefault: () => {}});

  expect(props.changeFilter).toHaveBeenCalledWith({operator: 'not in', values: []});
});

it('should render input fields depending on the selected operator', () => {
  const node = shallow(<StringInput {...props} />);

  expect(node.find('Checklist')).toExist();
  expect(node.find('ValueListInput')).not.toExist();

  node.setProps({filter: {operator: 'contains', values: ['']}});

  expect(node.find('Checklist')).not.toExist();
  expect(node.find('ValueListInput')).toExist();
});

it('should provide an includeUndefined field for the ValueListInput', () => {
  const node = shallow(
    <StringInput {...props} filter={{operator: 'not contains', values: ['A', null]}} />
  );

  expect(node.find('ValueListInput').prop('filter')).toEqual({
    operator: 'not contains',
    values: ['A'],
    includeUndefined: true,
  });
});

it('should parse the includeUndefined field from the ValueListInput', () => {
  const node = shallow(
    <StringInput {...props} filter={{operator: 'not contains', values: ['A']}} />
  );

  node
    .find('ValueListInput')
    .simulate('change', {operator: 'not contains', values: ['A'], includeUndefined: true});

  expect(props.changeFilter).toHaveBeenCalledWith({operator: 'not contains', values: ['A', null]});
});

it('should filter empty values when adding the filter', () => {
  const spy = jest.fn();

  StringInput.addFilter(
    spy,
    'variable',
    {name: 'varName', type: 'String'},
    {operator: 'contains', values: ['A', '', '', 'B', null]},
    {identifier: 'definition'}
  );

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'varName',
      type: 'String',
      data: {
        operator: 'contains',
        values: ['A', 'B', null],
      },
    },
    appliedTo: ['definition'],
  });
});

it('should add custom values to the list of available values', async () => {
  const node = shallow(<StringInput {...props} filter={{operator: 'in', values: ['A']}} />);

  await flushPromises();

  expect(node.find('Checklist').prop('allItems')).toContain('A');
});

it('should not add custom values if they are hidden behind a load more button', async () => {
  const node = shallow(
    <StringInput
      {...props}
      config={{getValues: () => ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L']}}
      filter={{operator: 'in', values: ['Z']}}
    />
  );

  await flushPromises();

  expect(node.find('Checklist').prop('allItems')).not.toContain('Z');
});

it('should allow adding custom values', async () => {
  const node = shallow(<StringInput {...props} filter={{operator: 'in', values: []}} />);

  await flushPromises();

  node.find('.customValueButton').simulate('click');
  node
    .find('.customValueInput')
    .find(Input)
    .simulate('change', {target: {value: 'newValue'}});
  node.find('.customValueInput').find(Button).simulate('click');

  expect(props.changeFilter).toHaveBeenCalledWith({operator: 'in', values: ['newValue']});
  expect(node).toIncludeText('Value added to list');
});

it('should not show previous contains value as custom value after switching operator', async () => {
  const node = shallow(<StringInput {...props} filter={{operator: 'contains', values: ['A']}} />);

  await flushPromises();

  node
    .find('.buttonRow')
    .find(Button)
    .at(0)
    .simulate('click', {preventDefault: () => {}});
  node.setProps({filter: {operator: 'in', values: []}});

  await flushPromises();

  expect(node.find('Checklist').prop('allItems')).not.toContain('A');
});

it('should parse existing values correctly without creating duplicates', async () => {
  const node = shallow(
    <StringInput
      {...props}
      config={{getValues: () => ['A', 'B']}}
      filter={{operator: 'in', values: [null, 'A']}}
    />
  );

  await flushPromises();

  expect(node.find('Checklist').prop('allItems')).toEqual([null, 'A', 'B']);
});

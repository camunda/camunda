/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import VariableFilter from './VariableFilter';

import {DateInput} from './date';

import {shallow} from 'enzyme';
import {Button} from 'components';

jest.mock('./date', () => {
  const DateInput = () => 'DateInput';

  DateInput.defaultFilter = {startDate: 'start', endDate: 'end'};
  DateInput.parseFilter = jest.fn();
  DateInput.addFilter = jest.fn();

  return {DateInput};
});

const props = {
  definitions: [{identifier: 'definition', key: 'procDefKey', versions: ['1'], tenantIds: [null]}],
  filterType: 'variable',
  config: {
    getVariables: jest.fn().mockReturnValue([
      {name: 'boolVar', type: 'Boolean'},
      {name: 'numberVar', type: 'Float'},
      {name: 'stringVar', type: 'String'},
    ]),
  },
};

const filterData = {
  type: 'variable',
  data: {
    name: 'foo',
    type: 'String',
    data: {
      operator: 'not in',
      values: ['value1', 'value2'],
    },
  },
  appliedTo: ['definition'],
};

it('should contain a modal', () => {
  const node = shallow(<VariableFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should disable add filter button if no variable is selected', () => {
  const node = shallow(<VariableFilter {...props} />);

  const buttons = node.find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
});

it('should take filter given by properties', async () => {
  const spy = jest.fn();
  const node = await shallow(<VariableFilter {...props} filterData={filterData} addFilter={spy} />);

  node.find('[primary]').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith(filterData);
});

it('should enable add filter button if variable selection is valid', async () => {
  const node = shallow(<VariableFilter {...props} />);

  await node.setState({
    valid: true,
    selectedVariable: {type: 'String', name: 'StrVar'},
  });
  const buttons = node.find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeFalsy(); // create filter
});

it('should create a new string filter', async () => {
  const spy = jest.fn();
  const node = await shallow(<VariableFilter {...props} addFilter={spy} />);

  node.setState({
    selectedVariable: {name: 'foo', type: 'String'},
    valid: true,
    filter: {
      operator: 'not in',
      values: ['value1', 'value2'],
    },
  });

  node.find('[primary]').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      data: {
        operator: 'not in',
        values: ['value1', 'value2'],
      },
    },
    appliedTo: ['definition'],
  });
});

it('should create a new  boolean filter', async () => {
  const spy = jest.fn();
  const node = await shallow(<VariableFilter {...props} addFilter={spy} />);

  node.setState({
    selectedVariable: {name: 'foo', type: 'Boolean'},
    valid: true,
  });

  node.find('[primary]').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'Boolean',
      data: {},
    },
    appliedTo: ['definition'],
  });
});

it('should use custom filter parsing logic from input components', () => {
  DateInput.parseFilter.mockClear();

  const existingFilter = {
    data: {
      type: 'Date',
      name: 'aDateVar',
      data: {
        type: 'static',
        start: 'someDate',
        end: 'someOtherDate',
      },
    },
    appliedTo: ['definition'],
  };
  shallow(<VariableFilter {...props} filterData={existingFilter} />);

  expect(DateInput.parseFilter).toHaveBeenCalledWith(existingFilter);
});

it('should use custom filter adding logic from input components', async () => {
  const spy = jest.fn();
  const node = await shallow(<VariableFilter {...props} addFilter={spy} />);

  const selectedVariable = {name: 'foo', type: 'Date'};
  const filter = {startDate: 'start', endDate: 'end'};
  node.setState({
    selectedVariable,
    valid: true,
    filter,
  });

  DateInput.addFilter.mockClear();

  node.find('[primary]').simulate('click', {preventDefault: jest.fn()});

  expect(DateInput.addFilter).toHaveBeenCalledWith(
    spy,
    'variable',
    selectedVariable,
    filter,
    props.definitions[0]
  );
});

it('should load available variables', () => {
  shallow(<VariableFilter {...props} />);

  expect(props.config.getVariables).toHaveBeenCalled();
});

it('should contain a typeahead with the available variables', async () => {
  const node = shallow(<VariableFilter {...props} />);

  props.config.getVariables.mockReturnValueOnce([{id: 'varA'}, {id: 'varB'}, {id: 'varC'}]);
  await node.instance().componentDidMount();

  expect(node.find('Typeahead')).toExist();
  expect(node.find({value: 'varA'})).toExist();
  expect(node.find({value: 'varB'})).toExist();
  expect(node.find({value: 'varC'})).toExist();
});

it('should allow rendering a pretext if provided', () => {
  const spy = jest.fn().mockReturnValue(<span className="pretext">pretext value</span>);
  const node = shallow(<VariableFilter {...props} filterData={filterData} getPretext={spy} />);

  expect(spy).toHaveBeenCalledWith({id: undefined, name: 'foo', type: 'String'});
  expect(node.find('.pretext')).toExist();
});

it('should allow rendering a posttext if provided', () => {
  const spy = jest.fn().mockReturnValue(<span className="posttext">posttext value</span>);
  const node = shallow(<VariableFilter {...props} filterData={filterData} getPosttext={spy} />);

  expect(spy).toHaveBeenCalledWith({id: undefined, name: 'foo', type: 'String'});
  expect(node.find('.posttext')).toExist();
});

it('should allow forcing the add button to be enabled', () => {
  const node = shallow(<VariableFilter {...props} />);

  expect(node.find(Button).last()).toBeDisabled();

  node.setProps({forceEnabled: () => true});

  expect(node.find(Button).last()).not.toBeDisabled();
});

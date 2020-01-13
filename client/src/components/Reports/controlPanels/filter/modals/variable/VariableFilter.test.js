/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import VariableFilter from './VariableFilter';

import {DateInput} from './date';
import {StringInput} from './string';

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
  processDefinitionKey: 'procDefKey',
  processDefinitionVersion: '1',
  filterType: 'variable',
  config: {
    getVariables: jest.fn().mockReturnValue([
      {name: 'boolVar', type: 'Boolean'},
      {name: 'numberVar', type: 'Float'},
      {name: 'stringVar', type: 'String'}
    ])
  }
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

it('should enable add filter button if filter for undefined is checked', async () => {
  const node = shallow(<VariableFilter {...props} />);

  await node.setState({
    valid: true,
    selectedVariable: {type: 'String', name: 'StrVar'},
    filterForUndefined: true
  });

  const buttons = node.find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy();
  expect(buttons.at(1).prop('disabled')).toBeFalsy();
});

it('should disable value input if filter for undefined is checked', async () => {
  const node = await shallow(<VariableFilter {...props} />);

  await node.setState({
    valid: true,
    selectedVariable: {type: 'String', name: 'StrVar'},
    filterForUndefined: true
  });

  expect(node.find(StringInput).prop('disabled')).toBeTruthy();
});

it('should take filter given by properties', async () => {
  const filterData = {
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      data: {
        operator: 'not in',
        values: ['value1', 'value2']
      },
      filterForUndefined: true
    }
  };
  const spy = jest.fn();
  const node = shallow(<VariableFilter {...props} filterData={filterData} addFilter={spy} />);

  node.find({variant: 'primary'}).simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      data: {
        operator: 'not in',
        values: ['value1', 'value2']
      },
      filterForUndefined: true
    }
  });
});

it('should enable add filter button if variable selection is valid', async () => {
  const node = shallow(<VariableFilter {...props} />);

  await node.setState({
    valid: true,
    selectedVariable: {type: 'String', name: 'StrVar'}
  });
  const buttons = node.find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeFalsy(); // create filter
});

it('should create a new filter', () => {
  const spy = jest.fn();
  const node = shallow(<VariableFilter {...props} addFilter={spy} />);

  node.setState({
    selectedVariable: {name: 'foo', type: 'String'},
    valid: true,
    filter: {
      operator: 'not in',
      values: ['value1', 'value2']
    }
  });

  node.find({variant: 'primary'}).simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      data: {
        operator: 'not in',
        values: ['value1', 'value2']
      },
      filterForUndefined: false
    }
  });
});

it('should create a new filter even if only filter for undefined is checked', () => {
  const spy = jest.fn();
  const node = shallow(<VariableFilter {...props} addFilter={spy} />);

  node.setState({
    selectedVariable: {name: 'foo', type: 'String'},
    valid: true,
    filterForUndefined: true
  });

  node.find({variant: 'primary'}).simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      data: {},
      filterForUndefined: true
    }
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
        end: 'someOtherDate'
      }
    }
  };
  shallow(<VariableFilter {...props} filterData={existingFilter} />);

  expect(DateInput.parseFilter).toHaveBeenCalledWith(existingFilter);
});

it('should use custom filter adding logic from input components', () => {
  const spy = jest.fn();
  const node = shallow(<VariableFilter {...props} addFilter={spy} />);

  const selectedVariable = {name: 'foo', type: 'Date'};
  const filter = {startDate: 'start', endDate: 'end'};
  node.setState({
    selectedVariable,
    valid: true,
    filter
  });

  DateInput.addFilter.mockClear();

  node.find({variant: 'primary'}).simulate('click', {preventDefault: jest.fn()});

  expect(DateInput.addFilter).toHaveBeenCalledWith(spy, selectedVariable, filter, false);
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

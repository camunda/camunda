/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import MultipleVariableFilter from './MultipleVariableFilter';
import {DateInput} from './date';

jest.mock('./date', () => {
  const DateInput = () => 'DateInput';

  DateInput.defaultFilter = {startDate: 'start', endDate: 'end'};
  DateInput.parseFilter = jest.fn().mockReturnValue({startDate: 'start', endDate: 'end'});
  DateInput.addFilter = jest.fn();
  DateInput.isValid = jest.fn();

  return {DateInput};
});

const props = {
  definitions: [{identifier: 'definition', key: 'procDefKey', versions: ['1'], tenantIds: [null]}],
  filterType: 'multipleVariable',
  config: {
    getVariables: jest.fn().mockReturnValue([
      {name: 'boolVar', type: 'Boolean'},
      {name: 'numberVar', type: 'Float'},
      {name: 'stringVar', type: 'String'},
    ]),
  },
};

const filterData = {
  type: 'multipleVariable',
  data: {
    data: [
      {
        name: 'foo',
        type: 'String',
        data: {
          operator: 'not in',
          values: ['value1', 'value2'],
        },
      },
      {},
    ],
  },
  appliedTo: ['definition'],
};

it('should contain a modal', () => {
  const node = shallow(<MultipleVariableFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should take filter given by properties', async () => {
  const spy = jest.fn();
  const node = await shallow(
    <MultipleVariableFilter {...props} filterData={filterData} addFilter={spy} />
  );

  await runAllEffects();

  node.find('.confirm').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith(filterData);
});

it('should use custom filter parsing logic from input components', async () => {
  DateInput.parseFilter.mockClear();

  const existingFilter = {
    data: {
      data: [
        {
          type: 'Date',
          name: 'aDateVar',
          data: {
            type: 'static',
            start: 'someDate',
            end: 'someOtherDate',
          },
        },
      ],
    },
    appliedTo: ['definition'],
  };
  shallow(<MultipleVariableFilter {...props} filterData={existingFilter} />);

  await runAllEffects();

  expect(DateInput.parseFilter).toHaveBeenCalledWith({
    data: existingFilter.data.data[0],
  });
});

it('should use custom filter adding logic from input components', async () => {
  const existingFilter = {
    data: {
      data: [
        {
          type: 'Date',
          name: 'foo',
          data: {startDate: 'start', endDate: 'end'},
        },
      ],
    },
    appliedTo: ['definition'],
  };

  const spy = jest.fn();
  const node = await shallow(
    <MultipleVariableFilter {...props} filterData={existingFilter} addFilter={spy} />
  );

  await runAllEffects();

  DateInput.addFilter.mockClear();

  node.find('.confirm').simulate('click', {preventDefault: jest.fn()});

  expect(DateInput.addFilter).toHaveBeenCalledWith(
    expect.any(Function),
    'multipleVariable',
    {name: 'foo', type: 'Date'},
    {startDate: 'start', endDate: 'end'},
    props.definitions[0]
  );
});

it('should load available variables', async () => {
  shallow(<MultipleVariableFilter {...props} />);

  await runAllEffects();

  expect(props.config.getVariables).toHaveBeenCalled();
});

it('should disable add filter button if one of the variable filters is invalid', async () => {
  DateInput.isValid.mockReturnValueOnce(false);
  const node = shallow(<MultipleVariableFilter {...props} />);

  node.find('FilterInstance').prop('updateFilterData')({
    type: 'Date',
    name: 'foo',
    data: {},
  });

  await runAllEffects();

  expect(node.find('.cancel').prop('disabled')).toBeFalsy(); // abort
  expect(node.find('.confirm').prop('disabled')).toBeTruthy(); // create filter
});

it('should create a new string filter', async () => {
  const spy = jest.fn();
  const node = await shallow(<MultipleVariableFilter {...props} addFilter={spy} />);
  await runAllEffects();

  node.find('FilterInstance').prop('updateFilterData')({
    type: 'String',
    name: 'foo',
    data: {
      operator: 'not in',
      values: ['value1', 'value2'],
    },
  });

  node.find('.confirm').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'multipleVariable',
    data: {
      data: [
        {
          name: 'foo',
          type: 'String',
          data: {
            operator: 'not in',
            values: ['value1', 'value2'],
          },
        },
      ],
    },
    appliedTo: ['definition'],
  });
});

it('should create a new  boolean filter', async () => {
  const spy = jest.fn();
  const node = await shallow(<MultipleVariableFilter {...props} addFilter={spy} />);
  await runAllEffects();

  node.find('FilterInstance').prop('updateFilterData')({
    type: 'Boolean',
    name: 'foo',
    data: {},
  });

  node.find('.confirm').simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalledWith({
    type: 'multipleVariable',
    data: {
      data: [{name: 'foo', type: 'Boolean', data: {}}],
    },
    appliedTo: ['definition'],
  });
});

it('should create another FilterInstance when clicking the OR button', async () => {
  const spy = jest.fn();
  const node = await shallow(<MultipleVariableFilter {...props} addFilter={spy} />);
  await runAllEffects();

  node.find('FilterInstance').prop('updateFilterData')({
    name: 'foo',
    type: 'String',
    data: {
      operator: 'not in',
      values: ['value1', 'value2'],
    },
  });

  node.find('.orButton').simulate('click');

  expect(node.find('FilterInstance').length).toBe(2);
});

it('should set/unset the expanded state of one of the added filters', async () => {
  const node = await shallow(<MultipleVariableFilter {...props} filterData={filterData} />);
  await runAllEffects();

  expect(node.find({expanded: true})).not.toExist();

  node.find('FilterInstance').at(0).prop('toggleExpanded')();
  expect(node.find('FilterInstance').at(0).prop('expanded')).toBe(true);

  node.find('FilterInstance').at(0).prop('toggleExpanded')();
  expect(node.find('FilterInstance').at(0).prop('expanded')).toBe(false);
});

it('should set the filter as expanded if it is being updated', async () => {
  const node = await shallow(<MultipleVariableFilter {...props} filterData={filterData} />);
  await runAllEffects();

  node.find('FilterInstance').at(1).prop('updateFilterData')({
    name: 'var2',
    type: 'String',
    data: {},
  });

  expect(node.find('FilterInstance').at(1).prop('expanded')).toBe(true);
});

it('should remove a variable filter', async () => {
  const node = await shallow(<MultipleVariableFilter {...props} filterData={filterData} />);
  await runAllEffects();

  node.find('FilterInstance').at(1).prop('onRemove')();

  expect(node.find('FilterInstance').length).toBe(1);
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import moment from 'moment';

import DateInput from './DateInput';

const props = {
  setValid: jest.fn(),
  changeFilter: jest.fn(),
  variable: {type: 'Date', name: 'aVariableName'},
  filter: {
    startDate: 'start',
    endDate: 'end',
  },
};

const exampleFilter = {
  type: 'variable',
  data: {
    name: 'aVariableName',
    type: 'Date',
    data: {
      start: moment('2018-07-09T00:00:00').format('YYYY-MM-DDTHH:mm:ss.SSSZZ'),
      end: moment('2018-07-12T23:59:59.999').format('YYYY-MM-DDTHH:mm:ss.SSSZZ'),
      type: 'fixed',
    },
  },
};

beforeEach(() => {
  props.changeFilter.mockClear();
});

it('should use the DateRangeInput', () => {
  const node = shallow(<DateInput {...props} />);

  expect(node.find('DateRangeInput')).toExist();
});

it('should parse an existing filter', () => {
  const parsed = DateInput.parseFilter(exampleFilter);

  expect(parsed.startDate).toBeDefined();
  expect(parsed.endDate).toBeDefined();
});

it('should convert a start and end-date to two compatible variable filters', () => {
  const spy = jest.fn();
  DateInput.addFilter(
    spy,
    'variable',
    {name: 'aVariableName', type: 'Date'},
    {
      valid: false,
      type: 'fixed',
      unit: '',
      customNum: '2',
      startDate: moment('2018-07-09'),
      endDate: moment('2018-07-12'),
    },
    false
  );

  expect(spy).toHaveBeenCalledWith(exampleFilter);
});

it('should show a date filter preview if the filter is valid', () => {
  const node = shallow(<DateInput {...props} filter={DateInput.parseFilter(exampleFilter)} />);

  expect(node.find('DateFilterPreview')).toExist();
});

it('should reset the filter when enabling the exclude undefined option', () => {
  const node = shallow(<DateInput {...props} />);

  node.find('UndefinedOptions').prop('changeExcludeUndefined')(true);

  expect(props.changeFilter).toHaveBeenCalledWith({
    ...DateInput.defaultFilter,
    excludeUndefined: true,
  });
});

it('should reset the exclude option when enabling the include option', () => {
  const node = shallow(
    <DateInput {...props} filter={{...DateInput.defaultFilter, excludeUndefined: true}} />
  );

  node.find('UndefinedOptions').prop('changeIncludeUndefined')(true);

  expect(props.changeFilter).toHaveBeenCalledWith({
    ...DateInput.defaultFilter,
    includeUndefined: true,
    excludeUndefined: false,
  });
});

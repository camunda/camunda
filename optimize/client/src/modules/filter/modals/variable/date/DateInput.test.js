/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {parseISO} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';

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
      start: format(parseISO('2018-07-09T00:00:00'), BACKEND_DATE_FORMAT),
      end: format(parseISO('2018-07-12T23:59:59.999'), BACKEND_DATE_FORMAT),
      type: 'fixed',
    },
  },
  appliedTo: ['definition'],
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
      type: 'between',
      unit: '',
      customNum: '2',
      startDate: parseISO('2018-07-09'),
      endDate: parseISO('2018-07-12'),
    },
    {identifier: 'definition'}
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

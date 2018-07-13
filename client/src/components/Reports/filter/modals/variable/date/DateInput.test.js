import React from 'react';
import {mount} from 'enzyme';
import moment from 'moment';

import DateInput from './DateInput';

jest.mock('components', () => {
  return {
    DatePicker: () => 'DatePicker'
  };
});

const props = {
  setValid: jest.fn(),
  changeFilter: jest.fn(),
  filter: {
    startDate: 'start',
    endDate: 'end'
  }
};

it('should show a DatePicker', () => {
  const node = mount(<DateInput {...props} />);

  expect(node).toIncludeText('DatePicker');
});

it('should parse an existing filter', () => {
  const parsed = DateInput.parseFilter([
    {data: {values: ['2018-07-09']}},
    {data: {values: ['2018-07-12']}}
  ]);

  expect(parsed.startDate).toBeDefined();
  expect(parsed.endDate).toBeDefined();
});

it('should convert a start and end-date to two compatible variable filters', () => {
  const spy = jest.fn();
  DateInput.addFilter(
    spy,
    {name: 'aVariableName', type: 'Date'},
    {
      startDate: moment('2018-07-09'),
      endDate: moment('2018-07-12')
    }
  );

  expect(spy).toHaveBeenCalledWith(
    {
      type: 'variable',
      data: {
        name: 'aVariableName',
        type: 'Date',
        operator: '>=',
        values: ['2018-07-09T00:00:00']
      }
    },
    {
      type: 'variable',
      data: {
        name: 'aVariableName',
        type: 'Date',
        operator: '<=',
        values: ['2018-07-12T23:59:59']
      }
    }
  );
});

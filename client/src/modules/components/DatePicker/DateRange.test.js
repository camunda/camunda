/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import DateRange from './DateRange';

import {mount} from 'enzyme';

jest.mock('react-date-range', () => {
  return {
    Calendar: props => <p className="Calendar">{`Calendar: props: ${Object.keys(props)}`}</p>
  };
});

const format = 'YYYY-MM-DD';
const minDate = 'min-date';
const maxDate = 'max-date';
let startDate;
let endDate;
let onDateChange;
let wrapper;

beforeEach(() => {
  startDate = moment('2012-12-15', format);
  endDate = moment('2018-05-02', format);
  onDateChange = jest.fn();
});

describe('with different dates', () => {
  beforeEach(() => {
    wrapper = mount(
      <DateRange
        minDate={minDate}
        maxDate={maxDate}
        startDate={startDate}
        endDate={endDate}
        onDateChange={onDateChange}
      />
    );
  });

  it('should display two Calendars', () => {
    expect(wrapper.find('.Calendar').length).toBe(2);
  });

  it('should not disable inner arrows', () => {
    expect(wrapper).toHaveState('innerArrowsDisabled', false);
  });

  it('should pass max and min dates properties to Calendars', () => {
    expect(wrapper).toIncludeText('minDate');
    expect(wrapper).toIncludeText('maxDate');
  });

  describe('changing month', () => {
    let linkCB;

    describe('start date Calendar', () => {
      beforeEach(() => {
        linkCB = wrapper.instance().changeStartMonth;
      });

      it('should change start link by one month forward when direction is 1', () => {
        linkCB(1);

        expect(wrapper.state('startLink').format(format)).toBe('2013-01-01');
        expect(wrapper.state('endLink').format(format)).toBe('2018-05-01');
      });

      it('should change start link by one month backward when direction is -1', () => {
        linkCB(-1);

        expect(wrapper.state('startLink').format(format)).toBe('2012-11-01');
        expect(wrapper.state('endLink').format(format)).toBe('2018-05-01');
      });
    });

    describe('end date Calendar', () => {
      beforeEach(() => {
        linkCB = wrapper.instance().changeEndMonth;
      });

      it('should change end link by one month forward when direction is 1', () => {
        linkCB(1);

        expect(wrapper.state('endLink').format(format)).toBe('2018-06-01');
        expect(wrapper.state('startLink').format(format)).toBe('2012-12-01');
      });

      it('should change end link by one month backward when direction is -11', () => {
        linkCB(-1);

        expect(wrapper.state('endLink').format(format)).toBe('2018-04-01');
        expect(wrapper.state('startLink').format(format)).toBe('2012-12-01');
      });
    });
  });
});

describe('with same dates', () => {
  beforeEach(() => {
    wrapper = mount(
      <DateRange startDate={startDate} endDate={startDate} onDateChange={onDateChange} />
    );
  });

  it('should disable inner arrows', () => {
    expect(wrapper).toHaveState('innerArrowsDisabled', true);
  });
});

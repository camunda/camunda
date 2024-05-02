/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, UserEvent} from 'modules/testing-library';
import {
  applyDateRange,
  pickDateTimeRange,
} from 'modules/testUtils/dateTimeRange';
import {getWrapper, MockDateRangeField} from './mocks';
import {tracking} from 'modules/tracking';

jest.unmock('modules/utils/date/formatDate');

describe('Date Range - tracking', () => {
  let trackSpy: jest.SpyInstance;
  let user: UserEvent;

  beforeEach(() => {
    trackSpy = jest.spyOn(tracking, 'track');
    user = render(<MockDateRangeField />, {wrapper: getWrapper()}).user;
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should track date picker, date input, time input', async () => {
    await user.click(screen.getByLabelText('Start Date Range'));
    expect(trackSpy).toHaveBeenNthCalledWith(1, {
      eventName: 'date-range-popover-opened',
      filterName: 'startDateRange',
    });

    await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
      fromTime: '11:22:33',
      toTime: '08:59:59',
    });

    await user.clear(screen.getByLabelText('From date'));
    await user.type(screen.getByLabelText('From date'), '2022-01-01');
    await user.clear(screen.getByLabelText('To date'));
    await user.type(screen.getByLabelText('To date'), '2022-12-01');
    await applyDateRange(user, screen);

    expect(trackSpy).toHaveBeenNthCalledWith(2, {
      eventName: 'date-range-applied',
      filterName: 'startDateRange',
      methods: {
        dateInput: true,
        datePicker: true,
        quickFilter: false,
        timeInput: true,
      },
    });
  });

  it('should track date picker', async () => {
    await user.click(screen.getByLabelText('Start Date Range'));
    expect(trackSpy).toHaveBeenNthCalledWith(1, {
      eventName: 'date-range-popover-opened',
      filterName: 'startDateRange',
    });

    await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
    });
    await applyDateRange(user, screen);

    expect(trackSpy).toHaveBeenNthCalledWith(2, {
      eventName: 'date-range-applied',
      filterName: 'startDateRange',
      methods: {
        dateInput: false,
        datePicker: true,
        quickFilter: false,
        timeInput: false,
      },
    });
  });

  it('should track date input, time input', async () => {
    await user.click(screen.getByLabelText('Start Date Range'));
    expect(trackSpy).toHaveBeenNthCalledWith(1, {
      eventName: 'date-range-popover-opened',
      filterName: 'startDateRange',
    });

    await user.type(screen.getByLabelText('From date'), '2022-01-01');
    await user.clear(screen.getByTestId('fromTime'));
    await user.type(screen.getByTestId('fromTime'), '12:30:00');
    await user.type(screen.getByLabelText('To date'), '2022-12-01');
    await user.clear(screen.getByTestId('toTime'));
    await user.type(screen.getByTestId('toTime'), '17:15:00');
    await applyDateRange(user, screen);

    expect(trackSpy).toHaveBeenNthCalledWith(2, {
      eventName: 'date-range-applied',
      filterName: 'startDateRange',
      methods: {
        dateInput: true,
        datePicker: false,
        quickFilter: false,
        timeInput: true,
      },
    });
  });

  it('should track date picker, time input', async () => {
    await user.click(screen.getByLabelText('Start Date Range'));
    expect(trackSpy).toHaveBeenNthCalledWith(1, {
      eventName: 'date-range-popover-opened',
      filterName: 'startDateRange',
    });

    await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
      fromTime: '11:22:33',
      toTime: '08:59:59',
    });
    await applyDateRange(user, screen);
    expect(trackSpy).toHaveBeenNthCalledWith(2, {
      eventName: 'date-range-applied',
      filterName: 'startDateRange',
      methods: {
        dateInput: false,
        datePicker: true,
        quickFilter: false,
        timeInput: true,
      },
    });
  });
});

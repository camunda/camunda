/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {
  applyDateRange,
  pickDateTimeRange,
} from 'modules/testUtils/carbon/dateTimeRange';
import {getWrapper, MockDateRangeField} from './mocks';

jest.unmock('modules/utils/date/formatDate');

describe('Date Range Field', () => {
  it('should close modal on cancel click', async () => {
    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});

    expect(screen.queryByTestId('date-range-modal')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByTestId('date-range-modal')).toHaveClass('is-visible');

    // getByRole does not work here because the date range modal portal is rendered to document.body
    await user.click(screen.getByText('Cancel'));
    expect(screen.queryByTestId('date-range-modal')).not.toBeInTheDocument();
  });

  it('should pick from and to dates and times', async () => {
    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});

    await user.click(screen.getByLabelText('Start Date Range'));

    const fromTime = '11:22:33';
    const toTime = '08:59:59';
    const {year, month, fromDay, toDay} = await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
      fromTime,
      toTime,
    });
    await applyDateRange(user, screen);

    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      `${year}-${month}-${fromDay} ${fromTime} - ${year}-${month}-${toDay} ${toTime}`
    );
  });

  it('should restore previous date on cancel', async () => {
    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

    const {year, month, fromDay, toDay} = await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
    });
    await applyDateRange(user, screen);

    const expectedValue = `${year}-${month}-${fromDay} 00:00:00 - ${year}-${month}-${toDay} 23:59:59`;
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      expectedValue
    );

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

    await user.click(screen.getByText('Cancel'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      expectedValue
    );
  });

  it('should set default values', async () => {
    const {user} = render(<MockDateRangeField />, {
      wrapper: getWrapper({
        startDateAfter: '2021-02-03T12:34:56',
        startDateBefore: '2021-02-06T01:02:03',
      }),
    });

    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      '2021-02-03 12:34:56 - 2021-02-06 01:02:03'
    );

    await user.click(screen.getByLabelText('Start Date Range'));

    expect(screen.getByLabelText('From date')).toHaveValue('2021-02-03');
    expect(screen.getByTestId('fromTime')).toHaveValue('12:34:56');
    expect(screen.getByLabelText('To date')).toHaveValue('2021-02-06');
    expect(screen.getByTestId('toTime')).toHaveValue('01:02:03');
  });

  it('should apply from and to dates', async () => {
    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});

    await user.click(screen.getByLabelText('Start Date Range'));
    await user.type(screen.getByLabelText('From date'), '2022-01-01');
    await user.click(screen.getByTestId('fromTime'));
    await user.clear(screen.getByTestId('fromTime'));
    await user.type(screen.getByTestId('fromTime'), '12:30:00');
    await user.type(screen.getByLabelText('To date'), '2022-12-01');
    await user.click(screen.getByTestId('toTime'));
    await user.clear(screen.getByTestId('toTime'));
    await user.type(screen.getByTestId('toTime'), '17:15:00');
    await applyDateRange(user, screen);

    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      '2022-01-01 12:30:00 - 2022-12-01 17:15:00'
    );
  });

  it('should show validation error on invalid character', async () => {
    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});
    const TIME_ERROR = 'Time has to be in format hh:mm:ss';

    await user.click(screen.getByLabelText('Start Date Range'));

    await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
    });

    expect(screen.queryByTestId('fromTime')).not.toBeInvalid();
    expect(screen.queryByText(TIME_ERROR)).not.toBeInTheDocument();
    expect(screen.queryByText('Apply')).not.toBeDisabled();

    await user.click(screen.getByTestId('fromTime'));
    await user.clear(screen.getByTestId('fromTime'));
    await user.type(screen.getByTestId('fromTime'), '12:30:xx');

    expect(screen.getByTestId('fromTime')).toBeInvalid();
    expect(screen.getByText(TIME_ERROR)).toBeInTheDocument();
    expect(screen.getByText('Apply')).toBeDisabled();
  });

  it('should show validation error on invalid time format', async () => {
    jest.useFakeTimers();

    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});
    const TIME_ERROR = 'Time has to be in format hh:mm:ss';

    await user.click(screen.getByLabelText('Start Date Range'));

    await pickDateTimeRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
    });

    await user.click(screen.getByTestId('fromTime'));
    await user.clear(screen.getByTestId('fromTime'));
    await user.type(screen.getByTestId('fromTime'), '1111');

    expect(screen.queryByTestId('fromTime')).not.toBeInvalid();
    expect(screen.queryByText(TIME_ERROR)).not.toBeInTheDocument();
    expect(screen.queryByText('Apply')).not.toBeDisabled();

    jest.runOnlyPendingTimers();

    expect(await screen.findByText(TIME_ERROR)).toBeInTheDocument();
    expect(screen.getByTestId('fromTime')).toBeInvalid();
    expect(screen.getByText('Apply')).toBeDisabled();

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});

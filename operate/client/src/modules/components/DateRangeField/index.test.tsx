/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen} from 'modules/testing-library';
import {
  applyDateRange,
  pickDateTimeRange,
} from 'modules/testUtils/dateTimeRange';
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
      `${year}-${month}-${fromDay} ${fromTime} - ${year}-${month}-${toDay} ${toTime}`,
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
      expectedValue,
    );

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

    await user.click(screen.getByText('Cancel'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      expectedValue,
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
      '2021-02-03 12:34:56 - 2021-02-06 01:02:03',
    );

    await user.click(screen.getByLabelText('Start Date Range'));

    expect(screen.getByLabelText('From date')).toHaveValue('2021-02-03');
    expect(screen.getByTestId('fromTime')).toHaveValue('12:34:56');
    expect(screen.getByLabelText('To date')).toHaveValue('2021-02-06');
    expect(screen.getByTestId('toTime')).toHaveValue('01:02:03');
  });

  // Unskip when this bug on Carbon side is fixed: https://github.com/carbon-design-system/carbon/issues/16125
  it.skip('should apply from and to dates', async () => {
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
      '2022-01-01 12:30:00 - 2022-12-01 17:15:00',
    );
  });

  it('should show validation error on invalid character', async () => {
    const {user} = render(<MockDateRangeField />, {wrapper: getWrapper()});
    const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

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
    const TIME_ERROR = 'Time has to be in the format hh:mm:ss';

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

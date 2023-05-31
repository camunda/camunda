/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatISODate} from 'modules/components/DateRangeField/formatDate';
import {UserEvent, Screen, waitFor} from 'modules/testing-library';

const pad = (value: String | Number) => {
  return String(value).padStart(2, '0');
};

const pickDateTimeRange = async ({
  user,
  screen,
  fromDay,
  toDay,
  fromTime,
  toTime,
}: {
  user: UserEvent;
  screen: Screen;
  fromDay: string;
  toDay: string;
  fromTime?: string;
  toTime?: string;
}) => {
  expect(screen.getByTestId('date-range-modal')).toHaveClass('is-visible');
  const monthName = document.querySelector('.cur-month')?.textContent;
  const year = document.querySelector<HTMLInputElement>('.cur-year')?.value;
  const month = new Date(`${monthName} 01, ${year}`).getMonth() + 1;

  await user.click(screen.getByLabelText('From date'));
  await user.click(screen.getByLabelText(`${monthName} ${fromDay}, ${year}`));
  await user.click(screen.getByLabelText('To date'));
  await user.click(screen.getByLabelText(`${monthName} ${toDay}, ${year}`));

  if (fromTime !== undefined) {
    await user.clear(screen.getByTestId('fromTime'));
    await user.type(screen.getByTestId('fromTime'), fromTime);
  }

  if (toTime !== undefined) {
    await user.clear(screen.getByTestId('toTime'));
    await user.type(screen.getByTestId('toTime'), toTime);
  }

  const fromTimeInput = screen.getByTestId('fromTime') as HTMLInputElement;
  const toTimeInput = screen.getByTestId('toTime') as HTMLInputElement;

  return {
    fromDay: pad(fromDay),
    toDay: pad(toDay),
    month: pad(month),
    fromDate: formatISODate(
      new Date(`${year}-${pad(month)}-${pad(fromDay)} ${fromTimeInput.value}`)
    ),
    toDate: formatISODate(
      new Date(`${year}-${pad(month)}-${pad(toDay)} ${toTimeInput.value}`)
    ),
    year,
  };
};

const applyDateRange = async (user: UserEvent, screen: Screen) => {
  const applyButton = screen.getByText('Apply');
  expect(applyButton).not.toBeDisabled();
  await user.click(applyButton);

  await waitFor(() =>
    expect(screen.queryByTestId('date-range-modal')).not.toBeInTheDocument()
  );
};

export {pickDateTimeRange, applyDateRange};

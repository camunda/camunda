/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatISODate} from 'modules/components/DateRangeField/formatDate';
import {waitFor, type UserEvent, type Screen} from 'modules/testing-library';

const pad = (value: string | number) => {
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
      new Date(`${year}-${pad(month)}-${pad(fromDay)} ${fromTimeInput.value}`),
    ),
    toDate: formatISODate(
      new Date(`${year}-${pad(month)}-${pad(toDay)} ${toTimeInput.value}`),
    ),
    year,
  };
};

const applyDateRange = async (user: UserEvent, screen: Screen) => {
  const applyButton = screen.getByText('Apply');
  expect(applyButton).not.toBeDisabled();
  await user.click(applyButton);

  await waitFor(() =>
    expect(screen.queryByTestId('date-range-modal')).not.toBeInTheDocument(),
  );
};

export {pickDateTimeRange, applyDateRange};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatISODate} from 'modules/components/DateRangeField/formatDate';
import {UserEvent, Screen} from 'modules/testing-library';

const pad = (value: String | Number) => {
  return String(value).padStart(2, '0');
};

const pickDateRange = async ({
  user,
  screen,
  fromDay,
  toDay,
}: {
  user: UserEvent;
  screen: Screen;
  fromDay: string;
  toDay: string;
}) => {
  expect(screen.getByTestId('popover')).toBeInTheDocument();

  const monthName = document.querySelector('.cur-month')?.textContent;
  const year = document.querySelector<HTMLInputElement>('.cur-year')?.value;
  const month = new Date(`${monthName} 01, ${year}`).getMonth() + 1;

  await user.click(screen.getByLabelText(`${monthName} ${fromDay}, ${year}`));
  await user.click(screen.getByLabelText(`${monthName} ${toDay}, ${year}`));

  return {
    fromDay: pad(fromDay),
    toDay: pad(toDay),
    month: pad(month),
    fromDate: formatISODate(new Date(`${year}-${pad(month)}-${pad(fromDay)}`)),
    toDate: formatISODate(new Date(`${year}-${pad(month)}-${pad(toDay)}`)),
    year,
  };
};

export {pickDateRange};

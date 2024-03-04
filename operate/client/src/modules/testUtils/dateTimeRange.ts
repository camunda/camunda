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

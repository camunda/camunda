/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

const pickDateTimeRange = async ({
  t,
  screen,
  fromDay,
  toDay,
  fromTime,
  toTime,
}: {
  t: TestController;
  screen: any;
  fromDay: string;
  toDay: string;
  fromTime?: string;
  toTime?: string;
}) => {
  await t.expect(screen.queryByTestId('popover').exists).ok();

  const monthName = await Selector('.cur-month').textContent;
  const year = await Selector('.cur-year').value;

  await t
    .click(screen.queryByText('From date'))
    .click(screen.queryByLabelText(`${monthName} ${fromDay}, ${year}`))
    .click(screen.queryByLabelText(`${monthName} ${toDay}, ${year}`));

  if (fromTime !== undefined) {
    await t.typeText(screen.getByTestId('fromTime'), fromTime, {replace: true});
  }

  if (toTime !== undefined) {
    await t.typeText(screen.getByTestId('toTime'), toTime, {replace: true});
  }
};

export {pickDateTimeRange};

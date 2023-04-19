/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './CallActivities.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';

fixture('Call Activities')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser).maximizeWindow();
  });

test('Navigate to called and parent process instances', async (t) => {
  const {
    initialData: {callActivityProcessInstance},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/processes/${callActivityProcessInstance.processInstanceKey}`
  );

  await t
    .expect(screen.queryByTestId('instance-header-skeleton').exists)
    .notOk();

  const withinInstanceHeader = within(screen.queryByTestId('instance-header'));

  await t
    .expect(
      withinInstanceHeader.queryByText(
        callActivityProcessInstance.processInstanceKey
      ).exists
    )
    .ok()
    .expect(withinInstanceHeader.getByText('Call Activity Process').exists)
    .ok()
    .click(
      screen.queryByRole('link', {description: /view all called instances/i})
    );

  const withinInstancesList = within(screen.queryByTestId('data-list'));

  await t
    .expect(withinInstancesList.queryAllByRole('row').count)
    .eql(1)
    .expect(withinInstancesList.getByText('Called Process').exists)
    .ok();

  const calledProcessInstanceId = await within(
    withinInstancesList.getAllByRole('row').nth(0)
  )
    .getAllByRole('cell')
    .nth(1).textContent;

  // Navigate to call activity instance
  await t.click(
    withinInstancesList.getByRole('link', {
      description: /view parent instance/i,
    })
  );

  // Expect correct header
  await t
    .expect(
      withinInstanceHeader.queryByText(
        callActivityProcessInstance.processInstanceKey
      ).exists
    )
    .ok()
    .expect(withinInstanceHeader.getByText('Call Activity Process').exists)
    .ok();

  const withinInstanceHistory = within(screen.getByTestId('instance-history'));

  // Expect correct instance history
  await t
    .expect(withinInstanceHistory.getByText('Call Activity Process').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('StartEvent_1').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('Call Activity').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('Event_1p0nsc7').exists)
    .ok();

  // Expect correct diagram
  await t
    .expect(
      within(screen.queryByTestId('diagram')).getByText('Call Activity').exists
    )
    .ok();

  // Navigate to called process instance
  await t.click(
    within(screen.queryByTestId('diagram')).getByText('Call Activity')
  );
  const withinPopover = within(screen.queryByTestId('popover'));
  await t
    .expect(withinPopover.queryByText(/Called Process Instance/).exists)
    .ok()
    .click(
      withinPopover.getByRole('link', {
        description: /view called process instance/i,
      })
    );

  // Expect correct header
  await t
    .expect(withinInstanceHeader.queryByText(calledProcessInstanceId).exists)
    .ok()
    .expect(withinInstanceHeader.getByText('Called Process').exists)
    .ok();

  // Expect correct instance history
  await t
    .expect(withinInstanceHistory.getByText('Called Process').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('Process started').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('Event_0y6k56d').exists)
    .ok();

  // Expect correct diagram
  await t
    .expect(
      within(screen.queryByTestId('diagram')).getByText('Process started')
        .exists
    )
    .ok();

  // Navigate to parent instance
  await t.click(
    withinInstanceHeader.getByRole('link', {
      description: /view parent instance/i,
    })
  );

  // Expect correct header
  await t
    .expect(
      withinInstanceHeader.queryByText(
        callActivityProcessInstance.processInstanceKey
      ).exists
    )
    .ok()
    .expect(withinInstanceHeader.getByText('Call Activity Process').exists)
    .ok();

  // Expect correct instance history
  await t
    .expect(withinInstanceHistory.getByText('Call Activity Process').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('StartEvent_1').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('Call Activity').exists)
    .ok()
    .expect(withinInstanceHistory.getByText('Event_1p0nsc7').exists)
    .ok();

  await t
    .expect(
      within(screen.queryByTestId('diagram')).getByText('Call Activity').exists
    )
    .ok();
});

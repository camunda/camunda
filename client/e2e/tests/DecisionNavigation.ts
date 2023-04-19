/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {demoUser} from './utils/Roles';
import {screen, within} from '@testing-library/testcafe';
import {setup} from './DecisionNavigation.setup';
import {wait} from './utils/wait';

fixture('Decision Navigation')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser).maximizeWindow();
  });

test('Navigation between process and decision', async (t) => {
  const {
    initialData: {
      processInstanceWithFailedDecision: {processInstanceKey},
    },
  } = t.fixtureCtx;

  await t
    .click(
      screen.queryByRole('link', {
        name: /processes/i,
      })
    )
    .click(
      screen.queryByRole('link', {
        description: `View instance ${processInstanceKey}`,
      })
    )
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .click(
      within(screen.getByTestId('diagram')).queryByText(/define approver/i)
    )
    .expect(screen.queryByTestId('popover').exists)
    .ok()
    .click(
      within(screen.getByTestId('popover')).queryByRole('link', {
        description: /view root cause decision invoice classification/i,
      })
    )
    .expect(screen.queryByTestId('decision-panel').exists)
    .ok()
    .expect(
      within(screen.getByTestId('decision-panel')).queryByText('Invoice Amount')
        .exists
    )
    .ok();

  const calledDecisionInstanceId = await within(
    screen.getByTestId('decision-instance-header')
  )
    .getAllByRole('cell')
    .nth(1).textContent;

  await t
    .click(screen.getByRole('button', {name: /close drd panel/i}))
    .click(
      screen.getByRole('link', {
        description: `View process instance ${processInstanceKey}`,
      })
    )
    .expect(screen.queryByTestId('instance-header').exists)
    .ok()
    .expect(
      within(screen.getByTestId('instance-header')).queryByText(
        processInstanceKey
      ).exists
    )
    .ok()
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .expect(
      within(screen.getByTestId('diagram')).queryByText(/define approver/i)
        .exists
    )
    .ok();

  await t
    .click(
      screen.getByRole('link', {
        name: /decisions/i,
      })
    )
    .click(
      screen.queryByRole('link', {
        description: `View decision instance ${calledDecisionInstanceId}`,
      })
    )
    .expect(screen.queryByTestId('decision-panel').exists)
    .ok()
    .expect(
      within(screen.getByTestId('decision-panel')).queryByText('Invoice Amount')
        .exists
    )
    .ok();
});

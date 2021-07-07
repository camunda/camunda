/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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

test('Navigate to called and parent instances', async (t) => {
  const {
    initialData: {callActivityProcessInstance},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/instances/${callActivityProcessInstance.processInstanceKey}`
  );

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
      withinInstanceHeader.getByRole('link', {
        name: /view all called instances/i,
      })
    );

  const withinInstancesList = within(screen.queryByTestId('instances-list'));

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

  await t
    .click(
      withinInstancesList.getByRole('link', {
        name: /view parent instance/i,
      })
    )
    .expect(
      withinInstanceHeader.queryByText(
        callActivityProcessInstance.processInstanceKey
      ).exists
    )
    .ok()
    .expect(withinInstanceHeader.getByText('Call Activity Process').exists)
    .ok();

  const withinDiagram = within(screen.queryByTestId('diagram'));

  await t
    .click(withinDiagram.queryByText('Call Activity'))
    .expect(withinDiagram.queryByText(/calledProcessInstanceId/).exists)
    .ok()
    .click(withinDiagram.getByRole('link', {name: /view called instance/i}));

  await t
    .expect(withinInstanceHeader.queryByText(calledProcessInstanceId).exists)
    .ok()
    .expect(withinInstanceHeader.getByText('Called Process').exists)
    .ok()
    .click(
      withinInstanceHeader.getByRole('link', {
        name: /view parent instance/i,
      })
    );

  await t
    .expect(
      withinInstanceHeader.queryByText(
        callActivityProcessInstance.processInstanceKey
      ).exists
    )
    .ok()
    .expect(withinInstanceHeader.getByText('Call Activity Process').exists)
    .ok();
});

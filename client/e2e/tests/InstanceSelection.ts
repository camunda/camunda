/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './InstanceSelection.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {instancesPage as InstancesPage} from './PageModels/Instances';

fixture('Select Instances')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t
      .useRole(demoUser)
      .maximizeWindow()
      .click(
        screen.queryByRole('link', {
          name: /view processes/i,
        })
      );
  });

test('Selection of instances are removed on filter selection', async (t) => {
  // select instances
  await t
    .click(InstancesPage.selectAllInstancesCheckbox)
    .expect(InstancesPage.selectAllInstancesCheckbox.checked)
    .ok();

  // instances are not selected after selecting finished instances filter

  await t
    .click(InstancesPage.Filters.finishedInstances.field)
    .expect(InstancesPage.selectAllInstancesCheckbox.checked)
    .notOk();

  // select instances
  await t
    .click(InstancesPage.selectAllInstancesCheckbox)
    .expect(InstancesPage.selectAllInstancesCheckbox.checked)
    .ok();

  // instances are not selected after applying instance id filter
  const instanceId = await within(screen.queryByTestId('data-list'))
    .getAllByRole('link', {name: /View instance/i})
    .nth(0).innerText;

  await displayOptionalFilter('Instance Id(s)');

  await InstancesPage.typeText(
    InstancesPage.Filters.instanceIds.field,
    instanceId.toString(),
    {
      paste: true,
    }
  );

  await t.expect(InstancesPage.selectAllInstancesCheckbox.checked).notOk();

  // select instances
  await t
    .click(InstancesPage.selectAllInstancesCheckbox)
    .expect(InstancesPage.selectAllInstancesCheckbox.checked)
    .ok();

  // instances are not selected after applying error message filter
  const errorMessage =
    "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'";

  await displayOptionalFilter('Error Message');

  await InstancesPage.typeText(
    InstancesPage.Filters.errorMessage.field,
    errorMessage,
    {paste: true}
  );

  await t.expect(InstancesPage.selectAllInstancesCheckbox.checked).notOk();
});

test('Selection of instances are not removed on sort', async (t) => {
  await t
    .click(InstancesPage.selectAllInstancesCheckbox)
    .expect(InstancesPage.selectAllInstancesCheckbox.checked)
    .ok();

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Process'}))
    .expect(InstancesPage.selectAllInstancesCheckbox.checked)
    .ok();
});

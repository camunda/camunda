/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {
  setup,
  cmFinishedInstancesCheckbox,
  cmInstanceIdsField,
} from './InstanceSelection.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {IS_NEW_FILTERS_FORM} from '../../src/modules/feature-flags';
import {displayOptionalFilter} from './utils/displayOptionalFilter';

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
          name: /view instances/i,
        })
      );
  });

test('Selection of instances are removed on filter selection', async (t) => {
  // select instances
  await t
    .click(screen.queryByRole('checkbox', {name: 'Select all instances'}))
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .ok();

  // instances are not selected after selecting finished instances filter

  const finishedInstancesCheckbox = IS_NEW_FILTERS_FORM
    ? cmFinishedInstancesCheckbox
    : screen.queryByRole('checkbox', {name: 'Finished Instances'});

  await t
    .click(finishedInstancesCheckbox)
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .notOk();

  // select instances
  await t
    .click(screen.queryByRole('checkbox', {name: 'Select all instances'}))
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .ok();

  // instances are not selected after applying instance id filter
  const instanceId = await within(screen.queryByTestId('instances-list'))
    .getAllByRole('link', {name: /View instance/i})
    .nth(0).innerText;

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Instance Id(s)');
  }

  const instanceIdsField = IS_NEW_FILTERS_FORM
    ? cmInstanceIdsField
    : screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      });

  await t.typeText(instanceIdsField, instanceId.toString(), {
    paste: true,
  });

  await t
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .notOk();

  // select instances
  await t
    .click(screen.queryByRole('checkbox', {name: 'Select all instances'}))
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .ok();

  // instances are not selected after applying error message filter
  const errorMessage =
    "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'";

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Error Message');
  }

  const errorMessageField = IS_NEW_FILTERS_FORM
    ? within(
        screen.queryByTestId('filter-error-message').shadowRoot()
      ).queryByRole('textbox')
    : screen.queryByRole('textbox', {name: /error message/i});

  await t.typeText(errorMessageField, errorMessage, {
    paste: true,
  });

  await t
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .notOk();
});

test('Selection of instances are not removed on sort', async (t) => {
  await t
    .click(screen.queryByRole('checkbox', {name: 'Select all instances'}))
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .ok();

  await t
    .click(screen.queryByRole('button', {name: 'Sort by processName'}))
    .expect(
      screen.queryByRole('checkbox', {name: 'Select all instances'}).checked
    )
    .ok();
});

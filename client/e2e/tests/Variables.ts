/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Variables.setup';
import {Selector} from 'testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';

fixture('Add/Edit Variables')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    const {
      initialData: {instance},
    } = t.fixtureCtx;

    await t.useRole(demoUser);
    await t.maximizeWindow();
    await t.navigateTo(
      `${config.endpoint}/#/instances/${instance.processInstanceKey}`
    );
  });

test('Validations for add/edit variable works correctly', async (t) => {
  // open single instance page, after clicking add new variable button see that save variable button is disabled and no spinner is displayed.
  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // add a new variable called test, see that save button is disabled, and no sipnner is displayed.
  await t
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'test')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // add a valid value to the newly added variable, see that save button is enabled and no spinner is displayed.
  await t
    .typeText(screen.getByRole('textbox', {name: /value/i}), '123')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the value of the variable and add some invalid value instead. see that save button is disabled and no spinner is displayed.
  await t
    .selectText(screen.getByRole('textbox', {name: /value/i}))
    .pressKey('delete')
    .typeText(screen.getByRole('textbox', {name: /value/i}), 'someTestValue')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the value of the variable and add some valid string value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(screen.getByRole('textbox', {name: /value/i}))
    .pressKey('delete')
    .typeText(screen.getByRole('textbox', {name: /value/i}), '"someTestValue"')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the value of the variable and add some valid json value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(screen.getByRole('textbox', {name: /value/i}))
    .pressKey('delete')
    .typeText(
      screen.getByRole('textbox', {name: /value/i}),
      '{"name": "value","found":true}'
    )
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the key of the newly added variable and see that save button is disabled and no spinner is displayed.
  await t
    .selectText(screen.getByRole('textbox', {name: /variable/i}))
    .pressKey('delete')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  await t.click(screen.getByRole('button', {name: 'Exit edit mode'}));
});

test('Edit variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;

  // open single instance page, after clicking the edit variable button see that save variable button is disabled.
  await t
    .click(screen.getByTestId('edit-variable-button'))
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok();

  // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
  await t
    .selectText(screen.getByTestId('edit-value'))
    .pressKey('delete')
    .typeText(screen.getByTestId('edit-value'), '"editedTestValue"')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(screen.getByRole('button', {name: 'Save variable'}))
    .expect(screen.getByTestId('edit-variable-spinner').exists)
    .ok()
    .expect(screen.getByTestId('operation-spinner').exists)
    .ok();

  // see that spinners both disappear after save variable operation completes.
  await t
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instance.processInstanceKey}`)
    .expect(Selector('[data-testid="testData"]').exists)
    .ok();
});

test('Add variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  // open single instance page, click add new variable button and see that save variable button is disabled.
  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok();

  // add a key to the newly added variable and see that save variable button is disabled and no spinner is displayed.
  await t
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'secondTestKey')
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // add a value to the newly added variable and see that save variable button is enabled and no spinner is displayed.
  await t
    .typeText(
      screen.getByRole('textbox', {name: /value/i}),
      '"secondTestValue"'
    )
    .expect(
      screen
        .getByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(screen.getByRole('button', {name: 'Save variable'}))
    .expect(screen.getByTestId('edit-variable-spinner').exists)
    .ok()
    .expect(screen.getByTestId('operation-spinner').exists)
    .ok();

  // see that spinners both disappear after save variable operation completes
  await t
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instance.processInstanceKey}`)
    .expect(screen.getByRole('cell', {name: 'secondTestKey'}).exists)
    .ok()
    .expect(screen.getByRole('cell', {name: '"secondTestValue"'}).exists)
    .ok();

  // go to instance page, filter and find the instance by added variable
  await t
    .navigateTo(`${config.endpoint}/#/instances?active=true&incidents=true`)
    .typeText(screen.getByRole('textbox', {name: 'Variable'}), 'secondTestKey')
    .typeText(screen.getByRole('textbox', {name: 'Value'}), '"secondTestValue"')
    .typeText(
      screen.getByRole('textbox', {
        name: /instance id\(s\) separated by space or comma/i,
      }),
      instance.processInstanceKey
    );

  await t
    .expect(
      within(screen.getByTestId('instances-list')).queryByRole('cell', {
        name: `View instance ${instance.processInstanceKey}`,
      }).exists
    )
    .ok();
});

test('Remove fields when instance is canceled', async (t) => {
  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .expect(screen.getByRole('textbox', {name: /variable/i}).exists)
    .ok()
    .expect(screen.getByRole('textbox', {name: /value/i}).exists)
    .ok();

  await t
    .click(screen.getByRole('button', {name: /^Cancel Instance/}))
    .expect(screen.getByTestId('operation-spinner').exists)
    .ok();

  await t
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk()
    .expect(screen.queryByRole('textbox', {name: /variable/i}).exists)
    .notOk()
    .expect(screen.queryByRole('textbox', {name: /value/i}).exists)
    .notOk();
});

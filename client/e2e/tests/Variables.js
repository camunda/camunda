/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import config from '../config';
import {login} from '../utils';
import {setup, createIncident} from './Variables.setup.js';
import * as Variables from './Variables.elements.js';
import {Selector} from 'testcafe';

fixture('Add/Edit Variables')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
  });

test.before(async (t) => {
  await t.wait(20000);
})('Validations for add/edit variable works correctly', async (t) => {
  const {
    initialData: {instanceId},
  } = t.fixtureCtx;

  await login(t);

  // open single instance page, after clicking add new variable button see that save variable button is disabled and no spinner is displayed.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instanceId}`)
    .click(Variables.addButton)
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // add a new variable called test, see that save button is disabled, and no sipnner is displayed.
  await t
    .typeText(Variables.addKey, 'test')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // add a valid value to the newly added variable, see that save button is enabled and no spinner is displayed.
  await t
    .typeText(Variables.addValue, '123')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .notOk()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // delete the value of the variable and add some invalid value instead. see that save button is disabled and no spinner is displayed.
  await t
    .selectText(Variables.addValue)
    .pressKey('delete')
    .typeText(Variables.addValue, 'someTestValue')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // delete the value of the variable and add some valid value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(Variables.addValue)
    .pressKey('delete')
    .typeText(Variables.addValue, '"someTestValue"')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .notOk()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // delete the key of the newly added variable and see that save button is disabled and no spinner is displayed.
  await t
    .selectText(Variables.addKey)
    .pressKey('delete')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  await t.click(Variables.cancelEdit);
});

test('Edit variables', async (t) => {
  const {
    initialData: {instanceId},
  } = t.fixtureCtx;

  await login(t);

  // open single instance page, after clicking the edit variable button see that save variable button is disabled.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instanceId}`)
    .click(Variables.editButton)
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok();

  // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
  await t
    .selectText(Variables.editText)
    .pressKey('delete')
    .typeText(Variables.editText, '"editedTestValue"')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .notOk()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(Variables.saveVariable)
    .expect(Variables.editVariableSpinner.exists)
    .ok()
    .expect(Variables.operationSpinner.exists)
    .ok();

  // see that spinners both disappear after save variable operation completes.
  await t
    .expect(Variables.editVariableSpinner.exists)
    .notOk({timeout: 20000})
    .expect(Variables.operationSpinner.exists)
    .notOk({timeout: 20000});

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instanceId}`)
    .expect(Selector('[data-test="testData"]').exists)
    .ok();
});

test('Add variables', async (t) => {
  const {
    initialData: {instanceId},
  } = t.fixtureCtx;

  await login(t);

  // open single instance page, click add new variable button and see that save variable button is disabled.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instanceId}`)
    .click(Variables.addButton)
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok();

  // add a key to the newly added variable and see that save variable button is disabled and no spinner is displayed.
  await t
    .typeText(Variables.addKey, 'secondTestKey')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .ok()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // add a value to the newly added variable and see that save variable button is enabled and no spinner is displayed.
  await t
    .typeText(Variables.addValue, '"secondTestValue"')
    .expect(Variables.saveVariable.hasAttribute('disabled'))
    .notOk()
    .expect(Variables.editVariableSpinner.exists)
    .notOk()
    .expect(Variables.operationSpinner.exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(Variables.saveVariable)
    .expect(Variables.editVariableSpinner.exists)
    .ok()
    .expect(Variables.operationSpinner.exists)
    .ok();

  // see that spinners both disappear after save variable operation completes
  await t
    .expect(Variables.editVariableSpinner.exists)
    .notOk({timeout: 20000})
    .expect(Variables.operationSpinner.exists)
    .notOk({timeout: 20000});

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`${config.endpoint}/#/instances/${instanceId}`)
    .expect(Selector('[data-test="secondTestKey"]').exists)
    .ok();
});

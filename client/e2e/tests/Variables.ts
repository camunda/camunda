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
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {instancesPage as InstancesPage} from './PageModels/Instances';
import {instancePage as InstancePage} from './PageModels/Instance';

fixture('Add/Edit Variables')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
  });

test('Validations for add variable works correctly', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(InstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  // open single instance page, after clicking add new variable button see that save variable button is disabled and no spinner is displayed.
  await t
    .click(InstancePage.addVariableButton)
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  // add a new variable called test, see that save button is disabled, and no sipnner is displayed.
  const nameField = within(InstancePage.newVariableNameField).queryByRole(
    'textbox'
  );

  await t
    .typeText(nameField, 'test')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .ok();

  // add a valid value to the newly added variable, see that save button is enabled and no spinner is displayed.
  const valueField = within(InstancePage.newVariableValueField).queryByRole(
    'textbox'
  );

  await t
    .typeText(valueField, '123')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  // delete the value of the variable and add some invalid value instead. see that save button is disabled and no spinner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, 'someTestValue')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .ok();

  // delete the value of the variable and add some valid string value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '"someTestValue"')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  // delete the value of the variable and add some valid json value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '{"name": "value","found":true}')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  // delete the key of the newly added variable and see that save button is disabled and no spinner is displayed.
  await t
    .selectText(nameField)
    .pressKey('delete')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  await t
    .expect(
      within(InstancePage.newVariableNameField).queryByText(
        'Name has to be filled'
      ).exists
    )
    .ok()
    .expect(
      within(InstancePage.newVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  await t.click(screen.queryByRole('button', {name: 'Exit edit mode'}));
});

test('Validations for edit variable works correctly', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(InstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  // open single instance page, after clicking the edit variable button see that save variable button is disabled.
  await t
    .click(screen.queryByTestId('edit-variable-button'))
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk()
    .expect(
      within(InstancePage.editVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  const valueField = within(InstancePage.editVariableValueField).queryByRole(
    'textbox'
  );

  // clear value field, see that save button is disabled, and no sipnner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk()
    .expect(
      within(InstancePage.editVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .ok();

  // type a valid value, see that save button is enabled and no spinner is displayed.
  await t
    .typeText(valueField, '123')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk()
    .expect(
      within(InstancePage.editVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  // delete the value of the variable and add some invalid value instead. see that save button is disabled and no spinner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, 'someTestValue')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk()
    .expect(
      within(InstancePage.editVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .ok();

  // delete the value of the variable and add some valid string value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '"someTestValue"')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk()
    .expect(
      within(InstancePage.editVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  // delete the value of the variable and add some valid json value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '{"name": "value","found":true}')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk()
    .expect(
      within(InstancePage.editVariableValueField).queryByText(
        'Invalid input text'
      ).exists
    )
    .notOk();

  await t.click(screen.queryByRole('button', {name: 'Exit edit mode'}));
});

test('Edit variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;

  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(InstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  // open single instance page, after clicking the edit variable button see that save variable button is disabled.
  await t
    .click(screen.queryByTestId('edit-variable-button'))
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok();

  // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
  const valueField = within(InstancePage.editVariableValueField).queryByRole(
    'textbox'
  );

  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '"editedTestValue"')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(InstancePage.saveVariableButton)
    .expect(InstancePage.variableSpinner.exists)
    .ok()
    .expect(InstancePage.operationSpinner.exists)
    .ok();

  // see that spinners both disappear after save variable operation completes.
  await t
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`/processes/${instance.processInstanceKey}`)
    .expect(Selector('[data-testid="testData"]').exists)
    .ok();
});

test('Add variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;

  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(InstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  // open single instance page, click add new variable button and see that save variable button is disabled.
  await t
    .click(InstancePage.addVariableButton)
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok();

  // add a key to the newly added variable and see that save variable button is disabled and no spinner is displayed.
  const nameField = within(InstancePage.newVariableNameField).queryByRole(
    'textbox'
  );

  await t
    .typeText(nameField, 'secondTestKey')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  // add a value to the newly added variable and see that save variable button is enabled and no spinner is displayed.
  const valueField = within(InstancePage.newVariableValueField).queryByRole(
    'textbox'
  );

  await t
    .typeText(valueField, '"secondTestValue"')
    .expect(InstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(InstancePage.saveVariableButton)
    .expect(InstancePage.variableSpinner.exists)
    .ok()
    .expect(InstancePage.operationSpinner.exists)
    .ok();

  // see that spinners both disappear after save variable operation completes
  await t
    .expect(InstancePage.variableSpinner.exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`/processes/${instance.processInstanceKey}`)
    .expect(screen.queryByRole('cell', {name: 'secondTestKey'}).exists)
    .ok()
    .expect(screen.queryByRole('cell', {name: '"secondTestValue"'}).exists)
    .ok();

  // go to instance page, filter and find the instance by added variable
  await t.click(
    screen.queryByRole('link', {
      name: /view instances/i,
    })
  );

  await displayOptionalFilter('Instance Id(s)');
  await displayOptionalFilter('Variable');

  await InstancesPage.typeText(
    InstancesPage.Filters.variableName.field,
    'secondTestKey'
  );

  await InstancesPage.typeText(
    InstancesPage.Filters.variableValue.field,
    '"secondTestValue"'
  );

  await InstancesPage.typeText(
    InstancesPage.Filters.instanceIds.field,
    instance.processInstanceKey
  );

  await t
    .expect(
      within(screen.queryByTestId('data-list')).queryByRole('cell', {
        name: `View instance ${instance.processInstanceKey}`,
      }).exists
    )
    .ok();
});

test('Should not change add variable state when enter is pressed', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(InstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  await t.click(InstancePage.addVariableButton);

  const nameField = within(InstancePage.newVariableNameField).queryByRole(
    'textbox'
  );
  const valueField = within(InstancePage.newVariableValueField).queryByRole(
    'textbox'
  );

  await t.expect(nameField.exists).ok().expect(valueField.exists).ok();

  await t.pressKey('enter');

  await t.expect(nameField.exists).ok().expect(valueField.exists).ok();
});

test('Remove fields when instance is canceled', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(InstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  await t
    .click(InstancePage.addVariableButton)
    .expect(
      within(InstancePage.newVariableNameField).queryByRole('textbox').exists
    )
    .ok()
    .expect(
      within(InstancePage.newVariableValueField).queryByRole('textbox').exists
    )
    .ok();

  await t
    .click(screen.queryByRole('button', {name: /^Cancel Instance/}))
    .click(screen.queryByRole('button', {name: 'Apply'}))
    .expect(InstancePage.operationSpinner.exists)
    .ok();

  await t
    .expect(screen.queryByTestId('add-variable-name').exists)
    .notOk()
    .expect(screen.queryByTestId('add-variable-value').exists)
    .notOk()
    .expect(InstancePage.operationSpinner.exists)
    .notOk();
});

test('Infinite scrolling', async (t) => {
  const {
    initialData: {instanceWithManyVariables},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/processes/${instanceWithManyVariables.processInstanceKey}`
  );

  await t.expect(screen.queryByTestId('variables-list').exists).ok();

  const withinVariablesList = within(screen.getByTestId('variables-list'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(51);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('bx').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('bx'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(101);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('dv').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('dv'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(151);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('ft').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('ft'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(201);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('hr').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('hr'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(201);

  await t
    .expect(screen.queryByText('aa').exists)
    .notOk()
    .expect(screen.getByText('by').exists)
    .ok()
    .expect(screen.getByText('jp').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('by'));
  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(201);

  await t
    .expect(screen.queryByText('jp').exists)
    .notOk()
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('by').exists)
    .ok();
});

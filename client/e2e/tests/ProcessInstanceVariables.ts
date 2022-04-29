/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './ProcessInstanceVariables.setup';
import {Selector} from 'testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {processesPage as ProcessesPage} from './PageModels/Processes';
import {processInstancePage as ProcessInstancePage} from './PageModels/ProcessInstance';

fixture('Process Instance Variables')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
  });

test('Edit variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;

  await t.navigateTo(`/processes/${instance.processInstanceKey}`);

  await t
    .expect(ProcessInstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  // open process instance page, after clicking the edit variable button see that save variable button is disabled.
  await t
    .click(screen.queryByTestId('edit-variable-button'))
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok();

  // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
  const valueField = within(
    ProcessInstancePage.editVariableValueField
  ).queryByRole('textbox');

  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '"editedTestValue"')
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(ProcessInstancePage.saveVariableButton)
    .expect(ProcessInstancePage.variableSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .ok();

  // see that spinners both disappear after save variable operation completes.
  await t
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk()
    .expect(ProcessInstancePage.operationSpinner.exists)
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
    .expect(ProcessInstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  // open process instance page, click add new variable button and see that save variable button is disabled.
  await t
    .click(ProcessInstancePage.addVariableButton)
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok();

  // add a key to the newly added variable and see that save variable button is disabled and no spinner is displayed.
  const nameField = within(
    ProcessInstancePage.newVariableNameField
  ).queryByRole('textbox');

  await t
    .typeText(nameField, 'secondTestKey')
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .ok()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .notOk();

  // add a value to the newly added variable and see that save variable button is enabled and no spinner is displayed.
  const valueField = within(
    ProcessInstancePage.newVariableValueField
  ).queryByRole('textbox');

  await t
    .typeText(valueField, '"secondTestValue"')
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(ProcessInstancePage.saveVariableButton)
    .expect(ProcessInstancePage.variableSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .ok();

  // see that spinners both disappear after save variable operation completes
  await t
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk()
    .expect(ProcessInstancePage.operationSpinner.exists)
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
      name: /view processes/i,
    })
  );

  await displayOptionalFilter('Instance Id(s)');
  await displayOptionalFilter('Variable');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.variableName.field,
    'secondTestKey'
  );

  await ProcessesPage.typeText(
    ProcessesPage.Filters.variableValue.field,
    '"secondTestValue"'
  );

  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
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
    .expect(ProcessInstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  await t.click(ProcessInstancePage.addVariableButton);

  const nameField = within(
    ProcessInstancePage.newVariableNameField
  ).queryByRole('textbox');
  const valueField = within(
    ProcessInstancePage.newVariableValueField
  ).queryByRole('textbox');

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
    .expect(ProcessInstancePage.addVariableButton.hasAttribute('disabled'))
    .notOk();

  await t
    .click(ProcessInstancePage.addVariableButton)
    .expect(
      within(ProcessInstancePage.newVariableNameField).queryByRole('textbox')
        .exists
    )
    .ok()
    .expect(
      within(ProcessInstancePage.newVariableValueField).queryByRole('textbox')
        .exists
    )
    .ok();

  await t
    .click(screen.queryByRole('button', {name: /^Cancel Instance/}))
    .click(screen.queryByRole('button', {name: 'Apply'}))
    .expect(ProcessInstancePage.operationSpinner.exists)
    .ok();

  await t
    .expect(screen.queryByTestId('add-variable-name').exists)
    .notOk()
    .expect(screen.queryByTestId('add-variable-value').exists)
    .notOk()
    .expect(ProcessInstancePage.operationSpinner.exists)
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

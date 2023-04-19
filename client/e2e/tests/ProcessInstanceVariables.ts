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
  await t.click(screen.queryByTestId('edit-variable-button'));

  // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
  const valueField = within(
    ProcessInstancePage.editVariableValueField
  ).queryByRole('textbox');

  await t
    .selectText(valueField)
    .pressKey('delete')
    .typeText(valueField, '"editedTestValue"');

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk()
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

  // add a new variable
  await t
    .click(ProcessInstancePage.addVariableButton)
    .typeText(
      within(ProcessInstancePage.newVariableNameField).queryByRole('textbox'),
      'secondTestKey'
    )
    .typeText(
      within(ProcessInstancePage.newVariableValueField).queryByRole('textbox'),
      '"secondTestValue"'
    )
    .expect(ProcessInstancePage.saveVariableButton.hasAttribute('disabled'))
    .notOk();

  // click save variable button and see that both variable spinner and operation spinner are displayed.
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
      name: /processes/i,
    })
  );

  await displayOptionalFilter('Process Instance Key(s)');
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
    .expect(screen.queryByText('1 results found').exists)
    .ok()
    .expect(
      screen.queryByRole('link', {
        description: `View instance ${instance.processInstanceKey}`,
      }).exists
    )
    .ok();
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

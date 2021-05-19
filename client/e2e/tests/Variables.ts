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
    await t.useRole(demoUser);
    await t.maximizeWindow();
  });

test('Validations for add/edit variable works correctly', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/instances/${instance.processInstanceKey}`);

  await t
    .expect(
      screen
        .queryByRole('button', {name: 'Add variable'})
        .hasAttribute('disabled')
    )
    .notOk();

  // open single instance page, after clicking add new variable button see that save variable button is disabled and no spinner is displayed.
  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // add a new variable called test, see that save button is disabled, and no sipnner is displayed.
  await t
    .typeText(screen.queryByRole('textbox', {name: /name/i}), 'test')
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // add a valid value to the newly added variable, see that save button is enabled and no spinner is displayed.
  await t
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '123')
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the value of the variable and add some invalid value instead. see that save button is disabled and no spinner is displayed.
  await t
    .selectText(screen.queryByRole('textbox', {name: /value/i}))
    .pressKey('delete')
    .typeText(screen.queryByRole('textbox', {name: /value/i}), 'someTestValue')
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the value of the variable and add some valid string value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(screen.queryByRole('textbox', {name: /value/i}))
    .pressKey('delete')
    .typeText(
      screen.queryByRole('textbox', {name: /value/i}),
      '"someTestValue"'
    )
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the value of the variable and add some valid json value instead. see that save button is enabled and no spinner is displayed.
  await t
    .selectText(screen.queryByRole('textbox', {name: /value/i}))
    .pressKey('delete')
    .typeText(
      screen.queryByRole('textbox', {name: /value/i}),
      '{"name": "value","found":true}'
    )
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // delete the key of the newly added variable and see that save button is disabled and no spinner is displayed.
  await t
    .selectText(screen.queryByRole('textbox', {name: /name/i}))
    .pressKey('delete')
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  await t.click(screen.queryByRole('button', {name: 'Exit edit mode'}));
});

test('Edit variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;

  await t.navigateTo(`/instances/${instance.processInstanceKey}`);

  await t
    .expect(
      screen
        .queryByRole('button', {name: 'Add variable'})
        .hasAttribute('disabled')
    )
    .notOk();

  // open single instance page, after clicking the edit variable button see that save variable button is disabled.
  await t
    .click(screen.queryByTestId('edit-variable-button'))
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok();

  // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
  await t
    .selectText(screen.queryByTestId('edit-value'))
    .pressKey('delete')
    .typeText(screen.queryByTestId('edit-value'), '"editedTestValue"')
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(screen.queryByRole('button', {name: 'Save variable'}))
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .ok()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .ok();

  // see that spinners both disappear after save variable operation completes.
  await t
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`/instances/${instance.processInstanceKey}`)
    .expect(Selector('[data-testid="testData"]').exists)
    .ok();
});

test('Add variables', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/instances/${instance.processInstanceKey}`);

  await t
    .expect(
      screen
        .queryByRole('button', {name: 'Add variable'})
        .hasAttribute('disabled')
    )
    .notOk();

  // open single instance page, click add new variable button and see that save variable button is disabled.
  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .ok();

  // add a key to the newly added variable and see that save variable button is disabled and no spinner is displayed.
  await t
    .typeText(screen.queryByRole('textbox', {name: /name/i}), 'secondTestKey')
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
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
      screen.queryByRole('textbox', {name: /value/i}),
      '"secondTestValue"'
    )
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // click save variable button and see that both edit variable spinner and operation spinner are displayed.
  await t
    .click(screen.queryByRole('button', {name: 'Save variable'}))
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .ok()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .ok();

  // see that spinners both disappear after save variable operation completes
  await t
    .expect(screen.queryByTestId('edit-variable-spinner').exists)
    .notOk()
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk();

  // refresh the page and see the variable is still there.
  await t
    .navigateTo(`/instances/${instance.processInstanceKey}`)
    .expect(screen.queryByRole('cell', {name: 'secondTestKey'}).exists)
    .ok()
    .expect(screen.queryByRole('cell', {name: '"secondTestValue"'}).exists)
    .ok();

  // go to instance page, filter and find the instance by added variable
  await t
    .click(
      screen.queryByRole('listitem', {
        name: /running instances/i,
      })
    )
    .typeText(
      screen.queryByRole('textbox', {name: 'Variable'}),
      'secondTestKey'
    )
    .typeText(
      screen.queryByRole('textbox', {name: 'Value'}),
      '"secondTestValue"'
    )
    .typeText(
      screen.queryByRole('textbox', {
        name: /instance id\(s\) separated by space or comma/i,
      }),
      instance.processInstanceKey
    );

  await t
    .expect(
      within(screen.queryByTestId('instances-list')).queryByRole('cell', {
        name: `View instance ${instance.processInstanceKey}`,
      }).exists
    )
    .ok();
});

test('Should not change add variable state when enter is pressed', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/instances/${instance.processInstanceKey}`);

  await t
    .expect(
      screen
        .queryByRole('button', {name: 'Add variable'})
        .hasAttribute('disabled')
    )
    .notOk();

  await t.click(
    screen.queryByRole('button', {
      name: 'Add variable',
    })
  );

  await t
    .expect(screen.getByRole('textbox', {name: /name/i}).exists)
    .ok()
    .expect(screen.getByRole('textbox', {name: /value/i}).exists)
    .ok();

  await t.pressKey('enter');

  await t
    .expect(screen.getByRole('textbox', {name: /name/i}).exists)
    .ok()
    .expect(screen.getByRole('textbox', {name: /value/i}).exists)
    .ok();
});

test('Remove fields when instance is canceled', async (t) => {
  const {
    initialData: {instance},
  } = t.fixtureCtx;
  await t.navigateTo(`/instances/${instance.processInstanceKey}`);

  await t
    .expect(
      screen
        .queryByRole('button', {name: 'Add variable'})
        .hasAttribute('disabled')
    )
    .notOk();

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .expect(screen.queryByRole('textbox', {name: /name/i}).exists)
    .ok()
    .expect(screen.queryByRole('textbox', {name: /value/i}).exists)
    .ok();

  await t
    .click(screen.queryByRole('button', {name: /^Cancel Instance/}))
    .expect(screen.queryByTestId('operation-spinner').exists)
    .ok();

  await t
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk()
    .expect(screen.queryByRole('textbox', {name: /variable/i}).exists)
    .notOk()
    .expect(screen.queryByRole('textbox', {name: /value/i}).exists)
    .notOk();
});

test('Infinite scrolling', async (t) => {
  const {
    initialData: {instanceWithManyVariables},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/instances/${instanceWithManyVariables.processInstanceKey}`
  );

  await t.expect(screen.queryByTestId('variables-list').exists).ok();

  const withinVariablesList = within(screen.getByTestId('variables-list'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(52);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('bx').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('bx'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(102);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('dv').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('dv'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(152);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('ft').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('ft'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(202);

  await t
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('hr').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('hr'));

  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(202);

  await t
    .expect(screen.queryByText('aa').exists)
    .notOk()
    .expect(screen.getByText('by').exists)
    .ok()
    .expect(screen.getByText('jp').exists)
    .ok();

  await t.scrollIntoView(screen.getByText('by'));
  await t.expect(withinVariablesList.queryAllByRole('row').count).eql(202);

  await t
    .expect(screen.queryByText('jp').exists)
    .notOk()
    .expect(screen.getByText('aa').exists)
    .ok()
    .expect(screen.getByText('by').exists)
    .ok();
});

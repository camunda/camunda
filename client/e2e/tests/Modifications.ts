/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen, within} from '@testing-library/testcafe';
import {wait} from './utils/wait';
import {demoUser} from './utils/Roles';
import {setup} from './Modifications.setup';
import {processInstancePage as ProcessInstancePage} from './PageModels/ProcessInstance';
import {ClientFunction} from 'testcafe';

fixture('Modifications')
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await ClientFunction(() =>
      localStorage.setItem(
        'sharedState',
        JSON.stringify({hideModificationHelperModal: true})
      )
    )();
    await t.maximizeWindow();

    const {
      initialData: {instanceWithoutAnIncident},
    } = t.fixtureCtx;

    const processInstanceId = instanceWithoutAnIncident.processInstanceKey;
    await t.navigateTo(`/processes/${processInstanceId}`);
  });

test('Should apply/remove edit variable modifications', async (t) => {
  await t.expect(screen.queryByTestId('foo').exists).ok();
  await t.click(
    screen.getByRole('button', {
      name: /modify instance/i,
    })
  );

  await t
    .expect(screen.queryByText('Process Instance Modification Mode').exists)
    .ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getEditVariableFieldSelector('foo'),
    '1',
    {replace: true}
  );
  await t.pressKey('tab');

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/edit variable "foo"/i).exists)
    .ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getEditVariableFieldSelector('test'),
    '2',
    {replace: true}
  );
  await t.pressKey('tab');

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/edit variable "test"/i).exists)
    .ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getEditVariableFieldSelector('foo'),
    '3',
    {replace: true}
  );
  await t.pressKey('tab');

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/edit variable "foo"/i).exists)
    .ok();

  await t.expect(ProcessInstancePage.getEditVariableFieldValue('foo')).eql('3');

  // Undo last edit variable modification, and see value is updated to the previous value
  await t.click(
    screen.getByRole('button', {
      name: /undo/i,
    })
  );

  await t
    .expect(ProcessInstancePage.getEditVariableFieldValue('foo'))
    .eql('1')
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/edit variable "test"/i).exists)
    .ok();

  // Undo after navigating to another flow node, and see values are correct in the previous scope

  await t.click(
    within(screen.getByTestId('instance-history')).getByText(/never fails/i)
  );
  await t
    .expect(screen.queryByText(/The Flow Node has no Variables/i).exists)
    .ok();

  await t
    .click(
      screen.getByRole('button', {
        name: /undo/i,
      })
    )
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/edit variable "foo"/i).exists)
    .ok();

  await t.click(
    within(screen.getByTestId('instance-history')).getByText(
      /without incidents process/i
    )
  );
  await t
    .expect(screen.queryByTestId('foo').exists)
    .ok()
    .expect(ProcessInstancePage.getEditVariableFieldValue('test'))
    .eql('123')
    .expect(ProcessInstancePage.getEditVariableFieldValue('foo'))
    .eql('1');

  // Undo again, see last modification footer disappear, all variables have their initial values

  await t
    .click(
      screen.getByRole('button', {
        name: /undo/i,
      })
    )
    .expect(screen.queryByText('Last added modification:').exists)
    .notOk()
    .expect(ProcessInstancePage.getEditVariableFieldValue('test'))
    .eql('123')
    .expect(ProcessInstancePage.getEditVariableFieldValue('foo'))
    .eql('"bar"');

  // should edit a variable, remove it from the summary modal, see it disappeared from the variables panel and footer

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getEditVariableFieldSelector('foo'),
    '1',
    {replace: true}
  );

  await t
    .pressKey('tab')
    .click(screen.getByTestId('apply-modifications-button'));

  await t.expect(screen.queryByText('foo: 1').exists).ok();
  await t.click(
    screen.getByRole('button', {name: 'Delete variable modification'})
  );
  await t.click(screen.getByRole('button', {name: 'Cancel'}));

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .notOk()
    .expect(ProcessInstancePage.getEditVariableFieldValue('foo'))
    .eql('"bar"');
});

test('Should apply/remove add variable modifications', async (t) => {
  await t.expect(screen.queryByTestId('foo').exists).ok();
  await t.click(
    screen.getByRole('button', {
      name: /modify instance/i,
    })
  );

  await t
    .expect(screen.queryByText('Process Instance Modification Mode').exists)
    .ok();

  // add a new variable
  await t.click(screen.getByRole('button', {name: /add variable/i}));
  await t.expect(screen.queryByTestId('newVariables[0]').exists).ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    'test2'
  );
  await t.pressKey('tab');

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableValueFieldSelector('newVariables[0]'),
    '1'
  );
  await t.pressKey('tab');

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/add new variable "test2"/i).exists)
    .ok();

  // add another new variable

  await t.click(screen.getByRole('button', {name: /add variable/i}));
  await t.expect(screen.queryByTestId('newVariables[1]').exists).ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableNameFieldSelector('newVariables[1]'),
    'test3'
  );
  await t.pressKey('tab');

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableValueFieldSelector('newVariables[1]'),
    '2'
  );
  await t.pressKey('tab');

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/add new variable "test3"/i).exists)
    .ok();

  // edit first added variable

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    'test2-edited',
    {replace: true}
  );
  await t.pressKey('tab');

  await t
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/add new variable "test2-edited"/i).exists)
    .ok();

  await t
    .expect(ProcessInstancePage.getNewVariableNameFieldValue('newVariables[0]'))
    .eql('test2-edited');

  await t
    .expect(ProcessInstancePage.getNewVariableNameFieldValue('newVariables[1]'))
    .eql('test3');

  // Undo last edit variable modification, and see value is updated to the previous value
  await t.click(
    screen.getByRole('button', {
      name: /undo/i,
    })
  );

  await t
    .expect(ProcessInstancePage.getNewVariableNameFieldValue('newVariables[0]'))
    .eql('test2')
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/add new variable "test3"/i).exists)
    .ok();

  // Undo after navigating to another flow node, and see last added variable is removed

  await t.click(
    within(screen.getByTestId('instance-history')).getByText(/never fails/i)
  );
  await t
    .expect(screen.queryByText(/The Flow Node has no Variables/i).exists)
    .ok();

  await t
    .click(
      screen.getByRole('button', {
        name: /undo/i,
      })
    )
    .expect(screen.queryByText('Last added modification:').exists)
    .ok()
    .expect(screen.queryByText(/add new variable "test2"/i).exists)
    .ok();

  await t.click(
    within(screen.getByTestId('instance-history')).getByText(
      /without incidents process/i
    )
  );

  await t
    .expect(screen.queryByTestId('newVariables[0]').exists)
    .ok()
    .expect(screen.queryByTestId('newVariables[1]').exists)
    .notOk()
    .expect(ProcessInstancePage.getNewVariableNameFieldValue('newVariables[0]'))
    .eql('test2')
    .expect(
      ProcessInstancePage.getNewVariableValueFieldValue('newVariables[0]')
    )
    .eql('1');

  // Undo again, see last modification footer disappear, new variable field is removed

  await t
    .click(
      screen.getByRole('button', {
        name: /undo/i,
      })
    )
    .expect(screen.queryByText('Last added modification:').exists)
    .notOk()
    .expect(screen.queryByTestId('newVariables[0]').exists)
    .notOk();

  // should add 2 new variables with same name and value to different scopes, remove one of it from the summary modal, see it disappeared from the variables panel and footer

  await t.click(screen.getByRole('button', {name: /add variable/i}));
  await t.expect(screen.queryByTestId('newVariables[0]').exists).ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    'test2'
  );
  await t.pressKey('tab');

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableValueFieldSelector('newVariables[0]'),
    '1'
  );

  await t
    .pressKey('tab')
    .click(
      within(screen.getByTestId('instance-history')).getByText(/never fails/i)
    );

  await t
    .expect(screen.queryByText(/The Flow Node has no Variables/i).exists)
    .ok();

  await t.click(screen.getByRole('button', {name: /add variable/i}));
  await t.expect(screen.queryByTestId('newVariables[0]').exists).ok();

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    'test2'
  );
  await t.pressKey('tab');

  await ProcessInstancePage.typeText(
    ProcessInstancePage.getNewVariableValueFieldSelector('newVariables[0]'),
    '1'
  );

  await t
    .pressKey('tab')
    .click(screen.getByTestId('apply-modifications-button'));

  await t.expect(screen.queryAllByText(/test2: 1/gi).count).eql(2);

  await t.click(
    screen
      .getAllByRole('button', {
        name: 'Delete variable modification',
      })
      .nth(1)
  );

  await t.click(screen.getByRole('button', {name: 'Cancel'}));

  await t.expect(screen.queryByTestId('newVariables[0]').exists).notOk();

  await t.click(
    within(screen.getByTestId('instance-history')).getByText(
      /without incidents process/i
    )
  );

  await t.expect(screen.queryByTestId('newVariables[0]').exists).ok();
});

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
import {IS_MODIFICATION_MODE_ENABLED} from '../../src/modules/feature-flags';
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

(IS_MODIFICATION_MODE_ENABLED ? test : test.skip)(
  'Should apply/remove edit variable modifications',
  async (t) => {
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

    await t
      .expect(ProcessInstancePage.getEditVariableFieldValue('foo'))
      .eql('3');

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
  }
);

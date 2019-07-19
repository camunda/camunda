/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';
import * as Alert from './Alerts.elements.js';

fixture('Alerts')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('add an alert', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.typeText(Report.nameEditField, 'Number Report', {replace: true});

  await u.save(t);

  await t.click(Alert.navItem);

  await t.click(Alert.newAlertButton);

  await t.typeText(Alert.nameField, 'Test Alert', {replace: true});
  await t.typeText(Alert.mailField, 'optimize-test@camunda.com', {replace: true});

  await t.click(Alert.reportTypeahead);
  await t.click(Alert.reportTypeaheadOption('Number Report'));

  await t.click(Alert.primaryModalButton);

  await t.expect(Alert.list.textContent).contains('Test Alert');
  await t.expect(Alert.list.textContent).contains('Number Report');
  await t.expect(Alert.list.textContent).contains('optimize-test@camunda.com');
});

test('edit an alert', async t => {
  await t.click(Alert.navItem);
  await t.click(Alert.editButton);

  await t.typeText(Alert.nameField, 'Edited Alert', {replace: true});

  await t.click(Alert.cancelButton);

  await t.expect(Alert.list.textContent).notContains('Edited Alert');

  await t.click(Alert.editButton);
  await t.typeText(Alert.nameField, 'Saved Alert', {replace: true});

  await t.click(Alert.primaryModalButton);

  await t.expect(Alert.list.textContent).contains('Saved Alert');
});

test('delete an alert', async t => {
  await t.click(Alert.navItem);
  await t.click(Alert.deleteButton);
  await t.click(Alert.primaryModalButton);

  await t.expect(Alert.list.textContent).notContains('Saved Alert');
});

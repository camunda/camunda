/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';
import * as Alert from './Alerts.elements.js';
import * as Homepage from './Homepage.elements.js';
import * as Collection from './Collection.elements.js';

fixture('Alerts').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create, edit, copy and remove an alert', async (t) => {
  await t.click(Homepage.createNewMenu).click(Homepage.option('Collection'));
  await t.typeText(Homepage.modalNameInput, 'Test Collection', {replace: true});
  await t.click(Homepage.carbonModalConfirmBtn);
  await t.click(Homepage.carbonModalConfirmBtn);

  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');

  await t.typeText(Report.nameEditField, 'Number Report', {replace: true});

  await u.save(t);

  await t.click(Collection.collectionBreadcrumb);
  await t.click(Collection.alertTab);

  // CREATE
  await t.click(Alert.newAlertButton);

  await t.typeText(Alert.inputWithLabel('Alert Name'), 'Test Alert', {replace: true});
  await t.typeText(Alert.inputWithLabel('Send Email to'), 'test@email.com test2@email.com', {
    replace: true,
  });

  await t.click(Alert.webhookDropdown);
  await t.click(Collection.typeaheadOption('testWebhook'));

  await t.click(Alert.reportTypeahead);
  await t.click(Alert.reportTypeaheadOption('Number Report'));

  await t.takeElementScreenshot(Alert.modal, 'additional-features/img/alert-modal-description.png');

  await t.click(Alert.primaryModalButton);

  await t.expect(Alert.list.textContent).contains('Test Alert');
  await t.expect(Alert.list.textContent).contains('Number Report');
  await t.expect(Alert.list.textContent).contains('test@email.com');

  await t
    .resizeWindow(1200, 500)
    .takeScreenshot('additional-features/img/alerts-overview.png', {fullPage: true})
    .maximizeWindow();

  // EDIT
  await t.hover(Alert.listItem);
  await t.click(Homepage.contextMenu(Alert.listItem));
  await t.click(Homepage.edit(Alert.listItem));

  await t.typeText(Alert.inputWithLabel('Alert Name'), 'Edited Alert', {replace: true});

  await t.click(Alert.cancelButton);

  await t.expect(Alert.list.textContent).notContains('Edited Alert');

  await t.hover(Alert.listItem);
  await t.click(Homepage.contextMenu(Alert.listItem));
  await t.click(Homepage.edit(Alert.listItem));
  await t.typeText(Alert.inputWithLabel('Alert Name'), 'Saved Alert', {replace: true});

  await t.click(Alert.primaryModalButton);

  await t.expect(Alert.list.textContent).contains('Saved Alert');

  // COPY
  await t.hover(Alert.listItem);
  await t.click(Homepage.contextMenu(Alert.listItem));
  await t.click(Homepage.copy(Alert.listItem));
  await t.typeText(Alert.inputWithLabel('Name of Copy'), 'Copied Alert', {replace: true});
  await t.click(Alert.primaryModalButton);
  await t.expect(Alert.list.textContent).contains('Copied Alert');

  // DELETE
  await t.hover(Alert.listItem);
  await t.click(Homepage.contextMenu(Alert.listItem));
  await t.click(Homepage.del(Alert.listItem));

  await t.click(Homepage.carbonModalConfirmBtn);

  await t.expect(Alert.list.textContent).notContains('Saved Alert');
});

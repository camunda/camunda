/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Alert from './Alerts.elements.js';
import * as Collection from './Collection.elements.js';
import * as Common from './Common.elements.js';

fixture('Alerts').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create, edit, copy and remove an alert', async (t) => {
  await t.click(Common.createNewMenu).click(Common.option('Collection'));
  await t.typeText(Common.modalNameInput, 'Test Collection', {replace: true});
  await t.click(Common.carbonModalConfirmBtn);
  await t.click(Common.carbonModalConfirmBtn);

  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');

  await t.typeText(Common.nameEditField, 'Number Report', {replace: true});

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
  await t.click(Common.typeaheadOption('testWebhook'));

  await t.click(Common.typeahead);
  await t.click(Common.typeaheadOption('Number Report'));

  await t.takeElementScreenshot(
    Common.modalContainer,
    'additional-features/img/alert-modal-description.png'
  );

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Alert.list.textContent).contains('Test Alert');
  await t.expect(Alert.list.textContent).contains('Number Report');
  await t.expect(Alert.list.textContent).contains('test@email.com');

  await t
    .resizeWindow(1200, 500)
    .takeScreenshot('additional-features/img/alerts-overview.png', {fullPage: true})
    .maximizeWindow();

  // EDIT
  await t.hover(Common.listItem);
  await t.click(Common.contextMenu(Common.listItem));
  await t.click(Common.edit(Common.listItem));

  await t.typeText(Alert.inputWithLabel('Alert Name'), 'Edited Alert', {replace: true});

  await t.click(Alert.cancelButton);

  await t.expect(Alert.list.textContent).notContains('Edited Alert');

  await t.hover(Common.listItem);
  await t.click(Common.contextMenu(Common.listItem));
  await t.click(Common.edit(Common.listItem));
  await t.typeText(Alert.inputWithLabel('Alert Name'), 'Saved Alert', {replace: true});

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Alert.list.textContent).contains('Saved Alert');

  // COPY
  await t.hover(Common.listItem);
  await t.click(Common.contextMenu(Common.listItem));
  await t.click(Common.copy(Common.listItem));
  await t.typeText(Alert.copyNameInput, 'Copied Alert', {replace: true});
  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Alert.list.textContent).contains('Copied Alert');

  // DELETE
  await t.hover(Common.listItem);
  await t.click(Common.contextMenu(Common.listItem));
  await t.click(Common.del(Common.listItem));

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Alert.list.textContent).notContains('Saved Alert');
});

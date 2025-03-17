/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Alert from './Alerts.elements.js';
import * as Collection from './Collection.elements.js';
import * as Common from './Common.elements.js';

fixture('Alerts')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await u.login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEntities);

test('create, edit, copy and remove an alert', async (t) => {
  await t.click(Common.createNewButton).click(Common.menuOption('Collection'));
  await t.typeText(Common.modalNameInput, 'Test Collection', {replace: true});
  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);

  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'Process instance', 'Count');

  await t.typeText(Common.nameEditField, 'Number Report', {replace: true});

  await u.save(t);

  await t.click(Common.collectionsPage);
  await t.click(Common.listItemLink('collection'));
  await t.click(Collection.alertTab);

  // CREATE
  await t.click(Alert.newAlertButton);

  await t.typeText(Alert.inputWithLabel('Alert name'), 'Test Alert', {replace: true});
  await t.typeText(Alert.inputWithLabel('Send email to'), 'demo@demo.com', {
    replace: true,
  });

  await t.click(Common.comboBox);
  await t.click(Common.carbonOption('Number Report'));

  await t.takeElementScreenshot(
    Common.modalContainer,
    'additional-features/img/alert-modal-description.png'
  );

  await t.click(Common.modalConfirmButton);

  await t.expect(Alert.list.textContent).contains('Test Alert');
  await t.expect(Alert.list.textContent).contains('Number Report');
  await t.expect(Alert.list.textContent).contains('demo@demo.com');

  await t
    .resizeWindow(1200, 500)
    .takeScreenshot('additional-features/img/alerts-overview.png', {fullPage: true})
    .maximizeWindow();

  // EDIT

  await t.hover(Alert.alertListItem);
  await t.click(Common.contextMenu(Alert.alertListItem));
  await t.click(Common.edit);

  await t.typeText(Alert.inputWithLabel('Alert name'), 'Edited Alert', {replace: true});

  await t.click(Alert.cancelButton);

  await t.expect(Alert.list.textContent).notContains('Edited Alert');

  await t.hover(Alert.alertListItem);
  await t.click(Common.contextMenu(Alert.alertListItem));
  await t.click(Common.edit);
  await t.typeText(Alert.inputWithLabel('Alert name'), 'Saved Alert', {replace: true});

  await t.click(Common.modalConfirmButton);

  await t.expect(Alert.list.textContent).contains('Saved Alert');

  // COPY
  await t.hover(Alert.alertListItem);
  await t.click(Common.contextMenu(Alert.alertListItem));
  await t.click(Common.copy);
  await t.typeText(Alert.copyNameInput, 'Copied Alert', {replace: true});
  await t.click(Common.modalConfirmButton);
  await t.expect(Alert.list.textContent).contains('Copied Alert');

  // DELETE
  await t.hover(Alert.alertListItem);
  await t.click(Common.contextMenu(Alert.alertListItem));
  await t.click(Common.del);

  await t.click(Common.modalConfirmButton);

  await t.expect(Alert.list.textContent).notContains('Saved Alert');
});

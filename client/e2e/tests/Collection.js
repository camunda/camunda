/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import {login, save} from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Dashboard from './Dashboard.elements.js';
import * as e from './Collection.elements.js';

fixture('Collection')
  .page(config.endpoint)
  .before(setup);

test('create a collection and entities inside it', async t => {
  await login(t);

  await t.click(Homepage.createNewMenu).click(Homepage.option('New Collection'));
  await t.typeText(Homepage.modalNameInput, 'Test Collection', {replace: true});
  await t.click(Homepage.confirmButton);

  await t.expect(e.collectionTitle.visible).ok();
  await t.expect(e.collectionTitle.textContent).contains('Test Collection');

  await t.click(e.createNewMenu);

  await t.expect(e.createNewMenu.textContent).notContains('New Collection');
  await t.expect(e.createNewMenu.textContent).contains('New Dashboard');
  await t.expect(e.createNewMenu.textContent).contains('New Report');

  await t.click(e.option('New Dashboard'));
  await save(t);
  await t.click(e.collectionBreadcrumb);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('New Dashboard');
});

test('renaming the collection', async t => {
  await login(t);

  await t.click(Homepage.collectionItem);

  await t.click(e.collectionContextMenu);
  await t.click(e.editCollectionNameButton);

  await t.typeText(Homepage.modalNameInput, 'another Collection Name', {replace: true});
  await t.click(Homepage.confirmButton);

  await t.expect(e.collectionTitle.textContent).contains('another Collection Name');
});

test('copy the collection', async t => {
  await login(t);

  await t.click(Homepage.collectionItem);

  await t.click(e.collectionContextMenu);
  await t.click(e.copyCollectionButton);

  await t.typeText(Homepage.modalNameInput, 'copied collection', {replace: true});

  await t.click(Homepage.confirmButton);

  await t.expect(e.collectionTitle.textContent).contains('copied collection');
});

test('adding a new user', async t => {
  await login(t);

  await t.click(Homepage.collectionItem);
  await t.click(e.userTab);

  await t.click(e.addUserButton);
  await t.click(e.optionsButton);
  await t.typeText(e.typeaheadInput, 'sales', {replace: true});
  await t.click(e.typeaheadOption('sales'));
  await t.click(e.confirmModalButton);

  await t.expect(e.groupItem.visible).ok();
  await t.expect(e.groupItem.textContent).contains('User Group');
  await t.expect(e.groupItem.textContent).contains('Sales');
  await t.expect(e.groupItem.textContent).contains('Viewer');

  await t.click(e.addUserButton);
  await t.typeText(e.typeaheadInput, 'mary', {replace: true});
  await t.click(e.typeaheadOption('mary'));
  await t.takeElementScreenshot(e.addUserModal, 'homepage/addUser.png');
  await t.click(e.roleOption('Editor'));
  await t.click(e.confirmModalButton);

  await t.click(e.addUserButton);
  await t.typeText(e.typeaheadInput, 'peter', {replace: true});
  await t.click(e.typeaheadOption('peter'));
  await t.click(e.roleOption('Editor'));
  await t.click(e.confirmModalButton);

  await t
    .resizeWindow(1150, 600)
    .takeElementScreenshot(e.userList, 'homepage/users.png')
    .maximizeWindow();
});

test('changing user permission', async t => {
  await login(t);

  await t.click(Homepage.collectionItem);
  await t.click(e.userTab);

  await t.hover(e.userItem.nth(0));
  await t.expect(Homepage.contextMenu(e.userItem.nth(0)).exists).notOk();

  await t.hover(e.groupItem);
  await t.expect(Homepage.contextMenu(e.groupItem).visible).ok();

  await t.click(Homepage.contextMenu(e.groupItem));
  await t.click(Homepage.edit(e.groupItem));

  await t.click(e.roleOption('Manager'));
  await t.click(e.confirmModalButton);

  await t.hover(e.userItem.nth(0));
  await t.expect(Homepage.contextMenu(e.userItem.nth(0)).visible).ok();

  await t.click(Homepage.contextMenu(e.userItem.nth(0)));
  await t.click(Homepage.edit(e.userItem.nth(0)));

  await t.click(e.roleOption('Viewer'));
  await t.click(e.confirmModalButton);

  await t.expect(e.addUserButton.exists).notOk();

  await t.click(e.entityTab);
  await t.click(Homepage.dashboardItem);

  await t.expect(Dashboard.editButton.exists).notOk();
});

test('deleting a collection', async t => {
  await login(t, 'john');

  await t.click(Homepage.collectionItem);

  await t.click(e.collectionContextMenu);
  await t.click(e.deleteCollectionButton);
  await t.click(Homepage.confirmButton);

  await t.expect(Homepage.collectionItem.exists).notOk();
});

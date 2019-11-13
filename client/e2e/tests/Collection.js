/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ensureLicense, cleanEntities} from '../setup';
import config from '../config';
import {login, save, getUser} from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Dashboard from './Dashboard.elements.js';
import * as e from './Collection.elements.js';

fixture('Collection')
  .page(config.endpoint)
  .before(ensureLicense)
  .beforeEach(login)
  .afterEach(cleanEntities);

async function createCollection(t, name = 'Test Collection') {
  await t.click(Homepage.createNewMenu).click(Homepage.option('New Collection'));
  await t.typeText(Homepage.modalNameInput, name, {replace: true});
  await t.click(Homepage.confirmButton);
}

test('create a collection and entities inside it', async t => {
  await createCollection(t, 'Test Collection');

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

test('renaming a collection', async t => {
  await createCollection(t);

  await t.click(e.collectionContextMenu);
  await t.click(e.editCollectionNameButton);

  await t.typeText(Homepage.modalNameInput, 'another Collection Name', {replace: true});
  await t.click(Homepage.confirmButton);

  await t.expect(e.collectionTitle.textContent).contains('another Collection Name');
});

test('copy a collection', async t => {
  await createCollection(t);

  await t.click(e.collectionContextMenu);
  await t.click(e.copyCollectionButton);

  await t.typeText(Homepage.modalNameInput, 'copied collection', {replace: true});

  await t.click(Homepage.confirmButton);

  await t.expect(e.collectionTitle.textContent).contains('copied collection');
});

test('user permissions', async t => {
  await createCollection(t);

  await t.click(e.createNewMenu);
  await t.click(e.option('New Dashboard'));
  await save(t);
  await t.click(e.collectionBreadcrumb);

  await t.click(e.userTab);

  await t.click(e.addButton);
  await t.click(e.optionsButton);
  await t.typeText(e.typeaheadInput, 'sales', {replace: true});
  await t.click(e.typeaheadOption('sales'));
  await t.click(e.confirmModalButton);

  await t.expect(e.groupItem.visible).ok();
  await t.expect(e.groupItem.textContent).contains('User Group');
  await t.expect(e.groupItem.textContent).contains('Sales');
  await t.expect(e.groupItem.textContent).contains('Viewer');

  await t.click(e.addButton);
  await t.typeText(e.typeaheadInput, 'mary', {replace: true});
  await t.click(e.typeaheadOption('mary'));
  await t.takeElementScreenshot(e.addUserModal, 'homepage/addUser.png');
  await t.click(e.roleOption('Editor'));
  await t.click(e.confirmModalButton);

  await t.click(e.addButton);
  await t.typeText(e.typeaheadInput, 'peter', {replace: true});
  await t.click(e.typeaheadOption('peter'));
  await t.click(e.roleOption('Editor'));
  await t.click(e.confirmModalButton);

  await t
    .resizeWindow(1150, 600)
    .takeElementScreenshot(e.userList, 'homepage/users.png')
    .maximizeWindow();

  // change permissions
  const managerName = await e.managerName.textContent;
  await t.hover(e.userItem(managerName));
  await t.expect(Homepage.contextMenu(e.userItem(managerName)).exists).notOk();

  await t.hover(e.groupItem);
  await t.expect(Homepage.contextMenu(e.groupItem).visible).ok();

  const {username} = getUser(t, 'user2');

  await t.click(e.addButton);
  await t.typeText(e.typeaheadInput, username, {replace: true});
  await t.click(e.typeaheadOption(username));
  await t.click(e.roleOption('Manager'));
  await t.click(e.confirmModalButton);

  await t.hover(e.userItem(managerName));
  await t.expect(Homepage.contextMenu(e.userItem(managerName)).visible).ok();

  await t.click(Homepage.contextMenu(e.userItem(managerName)));
  await t.click(Homepage.edit(e.userItem(managerName)));

  await t.click(e.roleOption('Viewer'));
  await t.click(e.confirmModalButton);

  await t.expect(e.addButton.exists).notOk();

  await t.click(e.entityTab);
  await t.click(Homepage.dashboardItem);

  await t.expect(Dashboard.editButton.exists).notOk();

  // delete collection
  await t.click(e.logoutButton);

  await login(t, 'user2');

  await t.click(Homepage.collectionItem);

  await t.click(e.collectionContextMenu);
  await t.click(e.deleteCollectionButton);
  await t.click(Homepage.confirmButton);

  await t.expect(Homepage.collectionItem.exists).notOk();
});

test('add, edit and delete sources', async t => {
  await createCollection(t);
  await t.click(e.sourcesTab);

  // add source by definition
  await t.click(e.addButton);
  const definitionName = 'Hiring Demo 5 Tenants';
  await t.typeText(e.typeaheadInput, definitionName, {replace: true});
  await t.click(e.typeaheadOption(definitionName));
  await t.click(e.checkbox('Select All'));
  await t.click(e.confirmModalButton);
  await t.expect(e.processItem.visible).ok();
  await t.expect(e.processItem.textContent).contains(definitionName);
  await t.expect(e.processItem.textContent).contains('Process');
  await t.expect(e.processItem.textContent).contains('engineering');

  // add source by tenant
  await t.click(e.addButton);
  const tenantName = 'engineering';
  await t.click(e.tenantSource);
  await t.typeText(e.typeaheadInput, tenantName, {replace: true});
  await t.click(e.typeaheadOption(tenantName));
  await t.click(e.checkbox('Beverages'));
  await t.click(e.checkbox('Book Request'));
  await t.click(e.confirmModalButton);
  await t.expect(e.processItem.visible).ok();
  await t.expect(e.decisionItem.visible).ok();
  await t.expect(e.processItem.nth(0).textContent).contains('Book Request');
  await t.expect(e.decisionItem.textContent).contains('Beverages');

  // edit source
  await t.hover(e.processItem.nth(1));
  await t.expect(Homepage.contextMenu(e.processItem.nth(1)).visible).ok();
  await t.click(Homepage.contextMenu(e.processItem.nth(1)));
  await t.click(Homepage.edit(e.processItem.nth(1)));
  await t.click(e.checkbox('engineering'));
  await t.click(e.confirmModalButton);
  await t.expect(e.processItem.nth(1).textContent).notContains('engineering');

  //delete source
  await t.hover(e.decisionItem);
  await t.click(Homepage.contextMenu(e.decisionItem));
  await t.click(Homepage.del(e.decisionItem));
  await t.click(e.confirmModalButton);
  await t.expect(e.decisionItem.exists).notOk();
});

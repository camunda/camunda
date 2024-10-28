/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import {login, save, getUser, createNewDashboard, addEditEntityDescription} from '../utils';
// import {Selector} from 'testcafe';

import * as Common from './Common.elements.js';
import * as e from './Collection.elements.js';
// import * as Report from './ProcessReport.elements.js';
// import * as Filter from './Filter.elements.js';

fixture('Collection')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEntities);

async function createCollection(t, name = 'Test Collection') {
  await t.click(Common.createNewButton).click(Common.menuOption('Collection'));
  await t.typeText(Common.modalNameInput, name, {replace: true});

  await t.click(Common.modalConfirmButton);
  await t.click(e.selectAllCheckbox);
  await t.click(Common.modalConfirmButton);
}

test('create a collection and entities inside it', async (t) => {
  await createCollection(t, 'Test Collection');

  await t.expect(e.collectionTitle.visible).ok();
  await t.expect(e.collectionTitle.textContent).contains('Test Collection');

  await t.click(e.entitiesTab);

  await t.click(Common.createNewButton);

  const createNewMenu = Common.menu('Create new').textContent;

  await t.expect(createNewMenu).notContains('Collection');
  await t.expect(createNewMenu).contains('Dashboard');
  await t.expect(createNewMenu).contains('Report');
  await t.click(Common.createNewButton);

  await createNewDashboard(t);
  const description = 'This is a description of the dashboard.';
  await addEditEntityDescription(t, description);

  await save(t);
  await t.click(Common.collectionsPage);
  await t.click(Common.listItemLink('collection'));

  await t.expect(Common.listItem('dashboard').visible).ok();
  await t.expect(Common.listItem('dashboard').textContent).contains('Blank dashboard');
  await t.expect(Common.listItem('dashboard').textContent).contains(description);
});

test('renaming a collection', async (t) => {
  await createCollection(t);

  await t.click(e.collectionContextMenu);
  await t.click(e.editCollectionNameButton);

  await t.typeText(Common.modalNameInput, 'another Collection Name', {replace: true});
  await t.click(Common.modalConfirmButton);

  await t.expect(e.collectionTitle.textContent).contains('another Collection Name');
});

test('copy a collection', async (t) => {
  await createCollection(t);

  await t.click(e.collectionContextMenu);
  await t.click(e.copyCollectionButton);

  await t.typeText(Common.modalNameInput, 'copied collection', {replace: true});

  await t.click(Common.modalConfirmButton);

  await t.expect(e.collectionTitle.textContent).contains('copied collection');
});

test('user permissions', async (t) => {
  await createCollection(t);

  await t.click(e.sourcesTab);

  await t.click(e.emptyStateAdd);
  const definitionName = 'Order process';
  await t.typeText(e.sourceModalSearchField, definitionName, {replace: true});
  await t.click(e.selectAllCheckbox);
  await t.click(Common.modalConfirmButton);

  await t.click(e.entitiesTab);

  await createNewDashboard(t);
  await save(t);
  await t.click(Common.collectionsPage);
  await t.click(Common.listItemLink('collection'));

  await t.click(e.userTab);
  const managerName = await e.userName(await Common.listItem('user')).textContent;

  await t.click(e.addButton);
  await t.typeText(Common.usersTypeahead, 'abse1978', {replace: true});
  await t.click(Common.carbonOption('abse1978').nth(1));
  // test the selection removal from the options
  await t.click(Common.carbonOption('abse1978').nth(1));
  await t.typeText(Common.usersTypeahead, 'demo', {replace: true});
  await t.click(Common.carbonOption('demo').nth(1)).pressKey('tab');
  await t.click(e.carbonRoleOption('Editor'));
  await t.takeElementScreenshot(Common.modalContainer, 'img/addUser.png');
  await t.click(Common.modalConfirmButton);

  await t
    .resizeWindow(1150, 650)
    .takeElementScreenshot(e.userList, 'img/users.png')
    .maximizeWindow();

  // change permissions
  await t.hover(Common.listItemWithText(managerName));
  await t
    .expect(Common.listItemTrigger(Common.listItemWithText(managerName), 'Edit').exists)
    .notOk();
  await t.expect(Common.listItemTrigger(Common.listItemWithText('demo'), 'Edit').visible).ok();

  const {username} = getUser(t, 'user2');

  await t.click(e.addButton);
  await t.typeText(Common.usersTypeahead, username, {replace: true});
  await t.click(Common.carbonOption(username).nth(1));
  await t.click(e.carbonRoleOption('Manager'));
  await t.click(Common.modalConfirmButton);

  await t.expect(Common.listItemTrigger(Common.listItemWithText(managerName), 'Edit').visible).ok();

  await t.click(Common.listItemTrigger(Common.listItemWithText(managerName), 'Edit'));
  await t.click(e.carbonRoleOption('Viewer'));
  await t.click(Common.modalConfirmButton);

  await t.expect(e.addButton.exists).notOk();

  await t.click(e.entityTab);
  await t.click(Common.listItemLink('dashboard', true));

  await t.expect(Common.editButton.exists).notOk();

  // bulk deleting users
  await t.click(e.usernameDropdown);
  await t.click(e.logoutButton);

  await login(t, 'user2');
  await t.click(Common.collectionsPage);

  await t.click(Common.listItemLink('collection'));
  await t.click(e.userTab);
  await t.expect(Common.listItem('user').count).eql(3);
  await t.click(Common.selectAllCheckbox.filterVisible());
  await t.click(e.bulkRemove.filterVisible());
  await t.click(Common.modalConfirmButton);
  await t.expect(Common.listItem('user').count).eql(1);

  // delete collection
  await t.click(e.collectionContextMenu);
  await t.click(e.deleteCollectionButton);
  await t.click(Common.modalConfirmButton);

  await t.expect(Common.listItem('collection').exists).notOk();
});

test('add, edit and delete sources', async (t) => {
  await createCollection(t);

  await t.click(e.sourcesTab);

  // add source by definition
  await t.click(e.emptyStateAdd);
  const definitionName = 'Big variable process';
  await t.typeText(e.sourceModalSearchField, 'big', {replace: true});
  await t.click(e.selectAllCheckbox);
  await t.click(Common.modalConfirmButton);
  await t.expect(e.processItem.visible).ok();
  await t.expect(e.processItem.textContent).contains(definitionName);
  await t.expect(e.processItem.textContent).contains('Process');
  // await t.expect(e.processItem.textContent).contains('engineering');

  // // add source by tenant
  // await t.click(e.addButton);
  // const tenantName = 'engineering';
  // await t.typeText(Common.comboBox, tenantName, {replace: true});
  // await t.click(Common.carbonOption(tenantName));
  // await t.click(e.itemCheckbox(5));
  // await t.click(e.itemCheckbox(6));
  // await t.takeElementScreenshot(Common.modalContainer, 'img/sourceByTenant.png');
  // await t.click(Common.modalConfirmButton);
  // await t.expect(e.processItem.visible).ok();

  // // edit source
  // await t.click(Common.listItemTrigger(e.processItem.nth(1), 'Edit'));
  // await t.click(e.checkbox('engineering'));
  // await t.click(Common.modalConfirmButton);
  // await t.expect(e.processItem.nth(1).textContent).notContains('engineering');

  // delete source
  await t.click(Common.listItemTrigger(e.processItem, 'Remove'));
  await t.click(Common.modalConfirmButton);
  await t.expect(e.processItem.count).eql(1);

  // bulk deleting sources
  await t.click(Common.listItemCheckbox(e.processItem.nth(0)));
  await t.click(e.bulkRemove.filterVisible());
  await t.click(Common.modalConfirmButton);
  await t.expect(e.processItem.filterVisible().exists).notOk();
});

// test('create new KPI report', async (t) => {
//   await t.click(Common.createNewButton).click(Common.menuOption('Collection'));
//   await t.typeText(Common.modalNameInput, 'test collection', {replace: true});
//   await t.click(Common.modalConfirmButton);
//   await t.click(Common.modalConfirmButton);

//   await t.click(Common.createNewButton);
//   await t.hover(Common.newReportOption);
//   await t.click(Common.submenuOption('Process KPI'));

//   await t.click(Common.kpiTemplateSelection);
//   await t.click(Common.carbonOption('Automation rate'));

//   await t.click(Common.templateModalProcessField);
//   await t.click(Common.carbonOption('Invoice Receipt with alternative correlation variable'));
//   await t.click(Common.modalConfirmButton);

//   await t.click(Common.kpiFilterButton);

//   await t.click(Report.flowNode('approveInvoice'));
//   await t.click(Report.flowNode('reviewInvoice'));
//   await t.click(Report.flowNode('prepareBankTransfer'));
//   await t.click(Common.modalConfirmButton);

//   await t.click(Common.kpiFilterButton);

//   await t.click(Filter.dateTypeSelect);
//   await t.click(Common.menuOption('This...'));
//   await t.click(Filter.unitSelect);
//   await t.click(Common.menuOption('year'));
//   await t.click(Common.modalConfirmButton);

//   await t.click(Common.modalConfirmButton);
//   await t.expect(Common.editButton.visible).ok();
// });

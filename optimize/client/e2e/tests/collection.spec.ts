/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {confirmDialog} from '../pages/components/dialog';
import {INCIDENT_PROCESS, ORDER_PROCESS} from '../seed/dataset';

test('create, rename, copy and delete a collection', async ({
  page,
  homePage,
  collectionPage,
  deleteAfterTest,
  uniqueName,
}) => {
  const name = uniqueName('Sales');

  await homePage.goto();
  await homePage.createCollection(name);
  await expect(collectionPage.heading).toHaveText(name);
  deleteAfterTest.collection(page.url());

  const renamed = uniqueName('Renamed sales');
  await collectionPage.rename(renamed);
  await expect(collectionPage.heading).toHaveText(renamed);

  const copyName = uniqueName('Copied sales');
  await collectionPage.copy(copyName);
  await expect(collectionPage.heading).toHaveText(copyName);
  deleteAfterTest.collection(page.url());

  await collectionPage.delete();
  await homePage.goto();
  await expect(homePage.list.link(renamed)).toBeVisible();
  await expect(homePage.list.link(copyName)).toBeHidden();
});

test('manage the data sources of a collection', async ({page, collection, collectionPage}) => {
  await collectionPage.goto(collection);
  await collectionPage.openTab('Data sources');
  await expect(collectionPage.list.rows).toHaveCount(2);

  await collectionPage.list.inlineRowAction(INCIDENT_PROCESS.name, 'Remove');
  await confirmDialog(page, /Remove/, /Remove/);
  await expect(collectionPage.list.rows).toHaveCount(1);

  await collectionPage.addDataSources(INCIDENT_PROCESS.name);
  await expect(collectionPage.list.row(INCIDENT_PROCESS.name)).toBeVisible();

  await collectionPage.list.selectAll();
  await collectionPage.list.bulkAction('Remove');
  await confirmDialog(page, /Remove/, /Remove/);
  await expect(collectionPage.list.row(ORDER_PROCESS.name)).toBeHidden();
});

test('grant and revoke access for another user', async ({collection, collectionPage, johnPage}) => {
  const createNewAsJohn = johnPage.getByRole('button', {name: 'Create new', exact: true});

  await collectionPage.goto(collection);
  await collectionPage.openTab('Users');
  await collectionPage.addUser('john', 'Viewer');
  await expect(collectionPage.list.row('john')).toContainText('Viewer');

  await johnPage.goto(collection.url);
  await expect(johnPage.getByRole('heading', {name: collection.name})).toBeVisible();
  await expect(createNewAsJohn).toBeHidden();

  await collectionPage.changeUserRole('john', 'Editor');
  await johnPage.reload();
  await expect(createNewAsJohn).toBeVisible();

  await collectionPage.removeUser('john');
  await johnPage.reload();
  await expect(johnPage.getByRole('heading', {name: collection.name})).toBeHidden();
});

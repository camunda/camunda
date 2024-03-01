/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEventProcesses} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as e from './Events.elements.js';
import * as Common from './Common.elements';

fixture('Events Processes')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await u.login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEventProcesses);

test('create a process from scratch', async (t) => {
  await t.click(e.navItem);
  await t.click(Common.emptyStateAdd);
  await t.click(Common.menuOption('Model a process'));
  await t.typeText(Common.nameEditField, 'Invoice Process', {replace: true});
  await t.click(e.firstEl);
  await t.click(e.activityTask);
  await t.click(e.saveButton);
  await t.expect(e.processName.textContent).eql('Invoice Process');
});

test('add sources, map and publish a process', async (t) => {
  // Creation
  await t.resizeWindow(1100, 800);
  await t.click(e.navItem);
  await t.click(Common.emptyStateAdd);
  await t.setFilesToUpload(e.fileInput, './resources/eventsProcess.bpmn');
  await t.click(Common.listItemLink('event-based process'));
  await t.click(Common.editButton);

  await t.typeText(Common.nameEditField, 'Event Invoice process', {replace: true});

  await t.takeScreenshot('additional-features/img/editMode.png');

  // adding sources

  await t.click(e.addSource);

  await t.typeText(e.processTypeahead, 'Invoice', {replace: true});
  await t.click(Common.carbonOption('Invoice Receipt with alternative correlation variable'));
  await t.expect(e.variableTypeahead.hasAttribute('disabled')).notOk();
  await t.click(e.variableTypeahead);
  await t.click(Common.carbonOption('longVar'));
  await t.click(Common.radioButton('Start and end flow node events'));

  await t.takeElementScreenshot(Common.modalContainer, 'additional-features/img/sourceModal.png');

  await t.click(Common.modalConfirmButton);

  await t.click(e.addSource);
  await t.click(e.externalEvents);
  await t.click(e.externalEventgroup(2));
  await t.click(e.externalEventgroup(3));
  await t.takeElementScreenshot(
    Common.modalContainer,
    'additional-features/img/externalEvents.png'
  );
  await t.click(Common.modalConfirmButton);

  await t.click(e.addSource);
  await t.click(e.externalEvents);
  await t.click(e.externalEventgroup(0));
  await t.click(Common.modalConfirmButton);

  await t.takeElementScreenshot(e.eventsTable, 'additional-features/img/eventsTable.png');

  // Mapping

  await t.click(e.startNode);
  await t.click(e.startEvent);

  await t.click(e.activity);
  await t.click(e.bankEnd);
  await t.click(e.bankStart);

  await t.click(e.endNode);
  await t.click(e.endEvent);

  await t.click(e.saveButton);

  await t.click(e.zoomButton);
  await t.click(e.zoomButton);

  await t.takeScreenshot('additional-features/img/processView.png');

  // publishing

  await t.click(e.publishButton);

  await t.takeElementScreenshot(Common.modalContainer, 'additional-features/img/publishModal.png');

  await t.click(e.permissionButton);

  await t.click(Common.usersTypeahead);
  // Using exact userId in the search to eliminate potential race condition on the identity being available in
  // the search cache. Id usage will always work regardless of the search cache state.
  await t.typeText(Common.usersTypeahead, 'john', {replace: true});
  await t.click(Common.carbonOption('john'));
  await t.typeText(Common.usersTypeahead, 'john', {replace: true});

  await t.takeElementScreenshot(
    Common.modalContainer.nth(1),
    'additional-features/img/usersModal.png'
  );
  await t.pressKey('Esc');

  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);
  await t.expect(Common.notification.exists).ok({timeout: 5000});
  await t.click(Common.notificationCloseButton(Common.notification));
  await t.expect(Common.notification.exists).notOk({timeout: 5000});

  // Listing
  await t.click(e.navItem);
  await t.hover(Common.listItem('event-based process'));
  await t.click(Common.contextMenu(Common.listItem('event-based process')));
  await t.takeScreenshot('additional-features/img/processList.png');

  // Edit Access
  await t.click(e.editAccess(e.invoiceEventProcess));
  await t.takeElementScreenshot(
    Common.modalContainer.nth(0),
    'additional-features/img/editAccess.png'
  );
});

test('auto generate a process', async (t) => {
  await t.click(e.navItem);
  await t.click(Common.emptyStateAdd);
  await t.click(Common.menuOption('Autogenerate'));
  await t.click(e.addEventSourceBtn);

  await t.typeText(e.processTypeahead, 'Invoice', {replace: true});
  await t.click(Common.carbonOption('Invoice Receipt'));

  await t.click(Common.radioButton('Business key'));
  await t.click(Common.modalConfirmButton);

  await t.click(e.addEventSourceBtn);
  await t.click(e.externalEvents);
  await t.click(Common.modalConfirmButton);

  await t.takeElementScreenshot(
    Common.modalContainer,
    'additional-features/img/auto-generation.png'
  );

  await t.click(Common.modalConfirmButton);

  await t.expect(e.diagram.visible).ok();
});

test('delete multiple external events', async (t) => {
  await t.click(e.navItem);
  await t.click(e.externalEventsTab);

  await t.resizeWindow(1100, 800);

  await t.takeScreenshot('additional-features/img/external-events.png', {fullPage: true});

  await t.click(e.eventCheckbox(0));
  await t.click(e.eventCheckbox(3));
  await t.hover(e.batchDeleteButton);

  await t.takeScreenshot('additional-features/img/deleting-events.png', {fullPage: true});

  await t.click(e.batchDeleteButton);
  await t.click(Common.modalConfirmButton);
});

test('edit event source', async (t) => {
  await t.click(e.navItem);
  await t.click(e.createDropdown);
  await t.click(Common.menuOption('Model a process'));
  await t.click(e.addSource);

  await t.typeText(e.processTypeahead, 'Invoice', {replace: true});
  await t.click(Common.carbonOption('Invoice Receipt with alternative correlation variable'));
  await t.click(e.variableTypeahead);
  await t.click(Common.carbonOption('longVar'));
  await t.click(Common.radioButton('Start and end flow node events'));
  await t.click(Common.modalConfirmButton);

  await t.click(e.sourceMenuButton('Invoice Receipt'));
  await t.click(Common.menuOption('Edit event source'));

  await t.expect(e.editingNotification.exists).ok();
  await t.expect(e.processTypeahead.hasAttribute('disabled')).ok();
  await t.expect(e.variableTypeahead.find('input').value).eql('longVar');

  await t.click(e.variableTypeahead);
  await t.click(Common.carbonOption('boolVar'));
  await t.click(Common.radioButton('Business key'));

  await t.click(Common.modalConfirmButton);
});

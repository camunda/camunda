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

fixture('Events Processes').page(config.endpoint).beforeEach(u.login).after(cleanEventProcesses);

test('create a process from scratch', async (t) => {
  await t.click(e.navItem);
  await t.click(e.createDropdown);
  await t.click(Common.option('Model a Process'));
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
  await t.click(e.createDropdown);
  await t.setFilesToUpload(e.fileInput, './resources/eventsProcess.bpmn');
  await t.click(e.entity('Event Invoice process'));
  await t.click(Common.editButton);

  await t.typeText(Common.nameEditField, 'Event Invoice process', {replace: true});

  await t.takeScreenshot('additional-features/img/editMode.png');

  // adding sources

  await t.click(e.addSource);

  await t.click(e.optionsButton(e.processTypeahead));
  await t.typeText(e.typeaheadInput(e.processTypeahead), 'Invoice', {replace: true});
  await t.click(
    e.typeaheadOption(e.processTypeahead, 'Invoice Receipt with alternative correlation variable')
  );
  await t.expect(e.optionsButton(e.variableTypeahead).hasAttribute('disabled')).notOk();
  await t.click(e.optionsButton(e.variableTypeahead));
  await t.click(e.typeaheadOption(e.variableTypeahead, 'longVar'));
  await t.click(e.startAndEndEvents);

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

  await t.click(e.optionsButton(e.usersTypeahead));
  // Using exact userId in the search to eliminate potential race condition on the identity being available in
  // the search cache. Id usage will always work regardless of the search cache state.
  await t.typeText(e.typeaheadInput(e.usersTypeahead), 'john', {replace: true});
  await t.click(e.typeaheadOption(e.usersTypeahead, 'john'));
  await t.typeText(e.typeaheadInput(e.usersTypeahead), 'john', {replace: true});

  await t.takeElementScreenshot(
    Common.modalContainer.nth(1),
    'additional-features/img/usersModal.png'
  );

  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);
  await t.expect(Common.notification.exists).ok({timeout: 5000});
  await t.click(Common.notificationCloseButton(Common.notification));
  await t.expect(Common.notification.exists).notOk({timeout: 5000});

  // Listing
  await t.click(e.navItem);
  await t.hover(e.entity('Event Invoice process'));
  await t.click(Common.contextMenu(e.invoiceEventProcess));
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
  await t.click(e.createDropdown);
  await t.click(Common.option('Autogenerate'));
  await t.click(e.addEventSourceBtn);

  await t.click(e.optionsButton(e.processTypeahead));
  await t.typeText(e.typeaheadInput(e.processTypeahead), 'Invoice', {replace: true});
  await t.click(e.typeaheadOption(e.processTypeahead, 'Invoice Receipt'));
  await t.click(e.businessKey);
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
  await t.click(e.selectionDropdown);
  await t.hover(Common.option('Delete'));

  await t.takeScreenshot('additional-features/img/deleting-events.png', {fullPage: true});

  await t.click(Common.option('Delete'));
  await t.click(Common.modalConfirmButton);
});

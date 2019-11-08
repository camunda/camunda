/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const dashboard = Selector('.ListItem.dashboard');
export const editButton = Selector('.edit-button');
export const reportDeleteButton = Selector('.DashboardObject .DeleteButton');
export const report = Selector('.ReportRenderer');
export const nameEditField = Selector('.EntityNameForm .name-input');
export const dashboardName = Selector('.DashboardView .name');
export const addButton = Selector('.AddButton');
export const externalSourceLink = Selector('.Button').withText('Add External Source');
export const externalSourceInput = Selector('.externalInput');
export const addReportButton = Selector('.ReportModal button').withText('Add Report');
export const externalReport = Selector('iframe');
export const formLabel = Selector('form label');
export const fullscreenButton = Selector('.fullscreen-button');
export const header = Selector('.Header');
export const themeButton = Selector('.theme-toggle');
export const fullscreenContent = Selector('.fullscreen');
export const shareButton = Selector('.share-button > .Button');
export const shareSwitch = Selector('.ShareEntity .Switch');
export const shareUrl = Selector('.ShareEntity .shareLink input');
export const deleteButton = Selector('.delete-button');
export const modalConfirmbutton = Selector('.Modal .confirm.Button');
export const reportModal = Selector('.ReportModal');

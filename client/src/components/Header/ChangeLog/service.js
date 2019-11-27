/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, put} from 'request';

export async function getMarkdownText(localeCode) {
  const response = await get(`api/localization/whatsnew`, {localeCode});

  return await response.text();
}

export async function isChangeLogSeen() {
  const response = await get('api/onboarding/whatsnew');

  return await response.json();
}

export async function setChangeLogAsSeen() {
  return await put('api/onboarding/whatsnew', {seen: true});
}

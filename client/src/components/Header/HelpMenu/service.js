/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, put} from 'request';
import {getOptimizeVersion} from 'config';

export async function getMarkdownText(localeCode) {
  const response = await get(`api/localization/whatsnew`, {
    version: await getOptimizeVersion(),
    localeCode,
  });

  return await response.text();
}

export async function isChangeLogSeen() {
  const response = await get('api/onboarding/whatsnew');

  return await response.json();
}

export async function setChangeLogAsSeen() {
  return await put('api/onboarding/whatsnew', {seen: true});
}

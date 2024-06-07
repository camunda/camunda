/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';

export async function loadTranslation(version: string, localeCode: string) {
  const response = await get(`api/localization`, {
    version,
    localeCode,
  });

  return await response.json();
}

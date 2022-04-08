/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post} from 'request';

export async function validateLicense() {
  const response = await get('api/license/validate');
  return await response.json();
}

export async function storeLicense(license) {
  const response = await post('api/license/validate-and-store', license, {
    headers: {'Content-Type': 'text/plain'},
  });
  return await response.json();
}

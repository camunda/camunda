/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {request} from 'request';

const outstandingRequests = [];

export function createOutstandingRequestPromise(payload) {
  return new Promise((resolve, reject) => {
    outstandingRequests.push({resolve, reject, payload});
  });
}

export async function redoOutstandingRequests() {
  outstandingRequests.forEach(async ({resolve, reject, payload}) => {
    try {
      resolve(await request(payload));
    } catch (e) {
      reject(e);
    }
  });
  resetOutstandingRequests();
}

export function resetOutstandingRequests() {
  outstandingRequests.length = 0;
}

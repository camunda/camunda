/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createInstance, createOperation} from 'modules/testUtils';

const INSTANCE = createInstance({
  id: '1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});
const ACTIVE_INSTANCE = createInstance({
  id: '2',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

export {INSTANCE, ACTIVE_INSTANCE};

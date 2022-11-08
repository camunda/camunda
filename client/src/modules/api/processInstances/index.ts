/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {request} from 'modules/request';

async function getOperation(batchOperationId: string) {
  return request({url: `/api/operations?batchOperationId=${batchOperationId}`});
}

async function fetchVariable(id: VariableEntity['id']) {
  return request({url: `/api/variables/${id}`});
}

export {getOperation, fetchVariable};

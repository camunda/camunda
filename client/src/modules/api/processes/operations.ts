/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';
import {BatchOperationDto} from '../sharedTypes';

async function deleteProcessDefinition(processDefinitionId: string) {
  return requestAndParse<BatchOperationDto>({
    url: `/api/processes/${processDefinitionId}`,
    method: 'DELETE',
  });
}

export {deleteProcessDefinition};

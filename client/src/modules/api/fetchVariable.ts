/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

const fetchVariable = async (
  processInstanceId: ProcessInstanceEntity['id'],
  variableId: VariableEntity['id']
) => {
  return requestAndParse<VariableEntity>({
    url: `/api/process-instances/${processInstanceId}/variables/${variableId}`,
  });
};

export {fetchVariable};

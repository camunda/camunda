/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DEFAULT_TENANT} from 'modules/constants';

const generateProcessKey = (bpmnProcessId: string, tenantId?: string) => {
  return `{${bpmnProcessId}}-{${tenantId ?? DEFAULT_TENANT}}`;
};

export {generateProcessKey};

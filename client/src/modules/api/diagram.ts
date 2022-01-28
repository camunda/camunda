/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from 'modules/request';

export const fetchProcessXML = async (
  processId: ProcessInstanceEntity['processId']
) => {
  return request({url: `/api/processes/${processId}/xml`});
};

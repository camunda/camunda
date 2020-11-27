/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'modules/request';

export const fetchWorkflowXML = async (workflowId: any) => {
  return get(`/api/workflows/${workflowId}/xml`);
};

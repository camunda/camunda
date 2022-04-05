/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../../config';

const ENDPOINTS = Object.freeze({
  createOperation(id: string) {
    return new URL(
      `/api/process-instances/${id}/operation`,
      config.endpoint
    ).toString();
  },
  login() {
    return new URL('/api/login', config.endpoint).toString();
  },
  getFlowNodeInstances() {
    return new URL('/api/flow-node-instances', config.endpoint).toString();
  },
});

export {ENDPOINTS};

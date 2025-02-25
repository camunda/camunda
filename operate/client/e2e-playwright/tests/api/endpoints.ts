/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {config} from '../../config';

const ENDPOINTS = Object.freeze({
  createOperation(id: string) {
    return new URL(
      `/api/process-instances/${id}/operation`,
      config.endpoint,
    ).toString();
  },
  login() {
    return new URL('/login', config.endpoint).toString();
  },
  getFlowNodeInstances() {
    return new URL('/api/flow-node-instances', config.endpoint).toString();
  },
});

export {ENDPOINTS};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
});

export {ENDPOINTS};

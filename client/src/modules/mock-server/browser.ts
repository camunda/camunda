/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {setupWorker} from 'msw';
import {handlers} from './handlers';

function startMocking() {
  const worker = setupWorker(...handlers);

  worker.stop();

  if (handlers.length > 0) {
    worker.start();
  }
}

export {startMocking};

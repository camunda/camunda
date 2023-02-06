/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {setupWorker} from 'msw';
import {handlers} from './handlers';

function startBrowserMocking() {
  const worker = setupWorker(...handlers);

  worker.stop();

  if (handlers.length > 0) {
    worker.start({
      onUnhandledRequest: 'bypass',
    });
  }
}

export {startBrowserMocking};

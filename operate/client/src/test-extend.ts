/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it as itBase} from 'vitest';
import {setupWorker} from 'msw/browser';

const worker = setupWorker();

const it = itBase.extend<{worker: typeof worker}>({
  worker: [
    async (_, use) => {
      await worker.start({
        onUnhandledRequest: 'error',
        quiet: true,
      });

      await use(worker);

      worker.resetHandlers();
    },
    {
      auto: true,
    },
  ],
});

export {it};

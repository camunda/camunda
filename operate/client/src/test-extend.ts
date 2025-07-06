import {it as itBase} from 'vitest';
import {setupWorker} from 'msw/browser';

const worker = setupWorker();

const it = itBase.extend<{worker: typeof worker}>({
  worker: [
    async ({}, use) => {
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

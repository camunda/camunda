/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it as itBase} from 'vitest';
import {isCommonAssetRequest} from 'msw';
import {setupWorker} from 'msw/browser';
import {cleanup} from 'vitest-browser-react';

const worker = setupWorker();

const it = itBase.extend<{
	worker: typeof worker;
}>({
	worker: [
		// eslint-disable-next-line no-empty-pattern
		async ({}, use) => {
			const unhandledRequests = new Set<string>();

			await worker.start({
				onUnhandledRequest(request, print) {
					if (isCommonAssetRequest(request)) {
						return;
					}
					unhandledRequests.add(`${request.method} ${new URL(request.url).pathname}`);
					print.error();
				},
				quiet: true,
			});

			await use(worker);

			await cleanup();
			worker.resetHandlers();
			worker.stop();

			if (unhandledRequests.size > 0) {
				throw new Error(
					`Unhandled requests (add a mock handler):\n${[...unhandledRequests].map((request) => `  - ${request}`).join('\n')}`,
				);
			}
		},
		{
			auto: true,
		},
	],
});

export {it};

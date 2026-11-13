/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {inject, it as itBase} from 'vitest';
import {http, HttpResponse, isCommonAssetRequest} from 'msw';
import {setupWorker} from 'msw/browser';
import {HttpNetworkFrame} from 'msw/experimental';
import {cleanup} from 'vitest-browser-react';

const worker = setupWorker();
const unhandledRequests = new Set<string>();

// Requests reach the page in order, so once this sentinel is answered every earlier request has been matched against the
// handlers that were active for it.
async function flushWorker() {
	worker.use(http.get('/__msw-flush', () => new HttpResponse(null, {status: 204})));
	const {status} = await fetch('/__msw-flush');
	worker.resetHandlers();
	return status;
}

function startWorker() {
	return worker.start({
		onUnhandledFrame({frame, defaults}) {
			if (!(frame instanceof HttpNetworkFrame) || isCommonAssetRequest(frame.data.request)) {
				return;
			}
			const {request} = frame.data;
			unhandledRequests.add(`${request.method} ${new URL(request.url).pathname}`);
			defaults.error();
		},
		quiet: true,
	});
}

const it = itBase.extend<{
	worker: typeof worker;
}>({
	worker: [
		// eslint-disable-next-line no-empty-pattern
		async ({}, use) => {
			unhandledRequests.clear();

			// Started once per file and never stopped, as the MSW Vitest Browser Mode recipe recommends. Stopping between
			// tests overlaps with the next start while the service worker still waits for the previous test's pending
			// requests, which leaves later requests unanswered.
			if (worker.readyState === 0) {
				await startWorker();
			}
			// A worker that stopped answering lets every request of the remaining tests in the file fall through to the dev
			// server, so a sentinel that is not answered by the worker means it has to be started again.
			if ((await flushWorker()) !== 204) {
				await worker.stop();
				await startWorker();
				await flushWorker();
			}

			await use(worker);

			await cleanup();
			// Requests still crossing the service worker when a short test ends would be matched after the handlers are
			// reset, or after the next test installs its own listener.
			await flushWorker();

			if (inject('failOnUnhandledRequests') && unhandledRequests.size > 0) {
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

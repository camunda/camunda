/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test as base} from '@playwright/test';
import {defineNetworkFixture, type NetworkFixture} from '@msw/playwright';
import type {AnyHandler} from 'msw';

type Fixtures = {
	handlers: Array<AnyHandler>;
	network: NetworkFixture;
};

const test = base.extend<Fixtures>({
	handlers: [[], {option: true}],
	network: [
		async ({context, handlers, baseURL}, use) => {
			const appOrigin = baseURL === undefined ? undefined : new URL(baseURL).origin;

			const network = defineNetworkFixture({
				context,
				handlers,
				onUnhandledRequest(request, print) {
					const url = new URL(request.url);
					if (
						request.method === 'GET' &&
						url.origin === appOrigin &&
						request.headers.get('accept')?.includes('text/html')
					) {
						return;
					}
					print.error();
				},
			});

			await network.enable();
			await use(network);
			await network.disable();
		},
		{auto: true},
	],
});

export {expect, test};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Response} from '@playwright/test';

abstract class View {
	protected readonly page: Page;

	constructor(page: Page) {
		this.page = page;
	}
}

abstract class BasePage extends View {
	abstract goto(...args: unknown[]): Promise<Response | null>;

	async seedSaasBootConfig({organizationId, clusterId}: {organizationId: string; clusterId: string}) {
		await this.page.route('**/*', async (route) => {
			if (route.request().resourceType() !== 'document') {
				return route.fallback();
			}

			const response = await route.fetch();
			const html = await response.text();
			const body = html.replace(
				'<html ',
				`<html data-organization-id="${organizationId}" data-cluster-id="${clusterId}" `,
			);

			return route.fulfill({response, body});
		});
	}
}

export {BasePage, View};

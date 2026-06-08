/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';

class Notifications {
	private page: Page;

	constructor(page: Page) {
		this.page = page;
	}

	getByNotificationTitle(title: string) {
		return this.page.getByRole('status').filter({hasText: title});
	}
}

export {Notifications};

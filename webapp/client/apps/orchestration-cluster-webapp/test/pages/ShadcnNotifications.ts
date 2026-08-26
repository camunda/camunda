/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {View} from './BasePage';

class ShadcnNotifications extends View {
	getByNotificationTitle(title: string) {
		return this.page.locator('[data-sonner-toast]').filter({hasText: title});
	}

	getActionButton(title: string, label: string) {
		return this.getByNotificationTitle(title).getByRole('button', {name: label});
	}
}

export {ShadcnNotifications};

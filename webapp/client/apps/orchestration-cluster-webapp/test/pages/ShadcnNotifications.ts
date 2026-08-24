/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {View} from './BasePage';

// The shadcn route tree renders notifications through the design system's
// <Toaster/> (sonner underneath), mounted in routes/shadcn/route.tsx — a
// different renderer than Notifications.ts's Carbon ActionableNotification/
// ToastNotification. Sonner exposes no ARIA role on individual toasts (only
// an aria-live region on their container), so these target its stable
// [data-sonner-toast] DOM hook instead of a role query. Actionable and
// non-actionable toasts render identically here, unlike Carbon's two
// component types, so one locator covers both.
class ShadcnNotifications extends View {
	getByNotificationTitle(title: string) {
		return this.page.locator('[data-sonner-toast]').filter({hasText: title});
	}

	getActionButton(title: string, label: string) {
		return this.getByNotificationTitle(title).getByRole('button', {name: label});
	}
}

export {ShadcnNotifications};

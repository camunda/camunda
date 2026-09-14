/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchSaasToken} from '#/shared/saas/fetchSaasToken';
import {parseNotificationFeed, type Notification} from './schemas';

type NotificationsConfig = {
	organizationId: string;
	url: string;
};

type AnalyticsEvent = 'notification-panel-opened' | 'notification-clicked-cta';

async function getSaasAccessToken(): Promise<string> {
	const token = await fetchSaasToken();
	if (token.length === 0) {
		throw new Error('SaaS access token is unavailable');
	}
	return token;
}

async function request(config: NotificationsConfig, path: string, init: RequestInit): Promise<Response> {
	for (let attempt = 0; attempt < 2; attempt += 1) {
		const token = await getSaasAccessToken();
		const response = await fetch(`${config.url.replace(/\/+$/u, '')}/${path}`, {
			...init,
			headers: {
				...(init.body === undefined ? {} : {'Content-Type': 'application/json'}),
				...init.headers,
				Authorization: `Bearer ${token}`,
			},
		});

		if (response.status === 401 && attempt === 0) {
			continue;
		}
		if (!response.ok) {
			throw new Error(`Notification request failed with status ${response.status}`);
		}
		return response;
	}

	throw new Error('Notification request failed with status 401');
}

async function getNotifications(config: NotificationsConfig): Promise<Notification[]> {
	const response = await request(config, `notifications/orgs/${encodeURIComponent(config.organizationId)}`, {
		method: 'GET',
	});
	return parseNotificationFeed(await response.json());
}

async function markNotificationsAsRead(config: NotificationsConfig, notificationIds: readonly string[]): Promise<void> {
	await request(config, 'notifications/batch/state/read', {
		method: 'PATCH',
		body: JSON.stringify({uuids: notificationIds}),
	});
}

async function dismissNotification(config: NotificationsConfig, notificationId: string): Promise<void> {
	await request(config, `notifications/${encodeURIComponent(notificationId)}/dismiss`, {method: 'PATCH'});
}

async function dismissAllNotifications(config: NotificationsConfig): Promise<void> {
	await request(config, `notifications/dismissAll?orgId=${encodeURIComponent(config.organizationId)}`, {
		method: 'PATCH',
	});
}

async function sendNotificationAnalytics(
	config: NotificationsConfig,
	event: AnalyticsEvent,
	identifier?: string,
): Promise<void> {
	await request(config, `analytics/${encodeURIComponent(config.organizationId)}/${event}`, {
		method: 'POST',
		body: JSON.stringify(identifier === undefined ? {} : {id: identifier}),
	});
}

export {
	dismissAllNotifications,
	dismissNotification,
	getNotifications,
	getSaasAccessToken,
	markNotificationsAsRead,
	sendNotificationAnalytics,
};
export type {AnalyticsEvent, NotificationsConfig};

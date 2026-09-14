/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const optionalStringSchema = z.preprocess(
	(value) => (typeof value === 'string' && value.length > 0 ? value : undefined),
	z.string().optional(),
);
const epochMillisecondsSchema = z.number().finite().int().nonnegative();

function normalizeHref(value: unknown): string | undefined {
	if (
		typeof value !== 'string' ||
		value.length === 0 ||
		value.length > 4_096 ||
		[...value].some((character) => {
			const codePoint = character.codePointAt(0) ?? 0;
			return codePoint <= 31 || codePoint === 127;
		})
	) {
		return undefined;
	}
	if (value.startsWith('/') && !value.startsWith('//')) {
		return value;
	}
	try {
		const url = new URL(value);
		return url.protocol === 'https:' && url.username === '' && url.password === '' ? value : undefined;
	} catch {
		return undefined;
	}
}

const notificationMetaSchema = z.object({
	identifier: optionalStringSchema,
	href: z.preprocess(normalizeHref, z.string().optional()),
	label: optionalStringSchema,
});

const notificationSchema = z.object({
	uuid: z.string().min(1),
	timestamp: epochMillisecondsSchema,
	type: z.enum(['org', 'individual', 'global']),
	title: z.string(),
	description: z.string(),
	state: z.enum(['new', 'read', 'dismissed', 'draft', 'scheduled']),
	orgId: optionalStringSchema,
	meta: z.preprocess((value) => value ?? undefined, notificationMetaSchema.optional()),
});

type Notification = z.infer<typeof notificationSchema>;

function parseNotificationFeed(value: unknown): Notification[] {
	const feed = z.array(z.unknown()).parse(value);
	const notifications = feed.flatMap((item) => {
		const result = notificationSchema.safeParse(item);
		return result.success ? [result.data] : [];
	});

	if (feed.length > 0 && notifications.length === 0) {
		throw new Error('Notification response contains no valid items');
	}

	return notifications;
}

export {parseNotificationFeed};
export type {Notification};

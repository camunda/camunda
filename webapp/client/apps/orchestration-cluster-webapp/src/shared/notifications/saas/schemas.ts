/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const optionalStringSchema = z.preprocess(
	(value) => (typeof value === 'string' ? value : undefined),
	z.string().optional(),
);
const epochMillisecondsSchema = z.number().finite().int().min(0).max(8_640_000_000_000_000);
const notificationStateSchema = z.enum(['new', 'read', 'dismissed', 'draft', 'scheduled']);
const notificationSourceSchema = z.enum([
	'console',
	'accounts',
	'modeler',
	'operate',
	'tasklist',
	'optimize',
	'marketing',
]);
const notificationTypeSchema = z.enum(['org', 'individual', 'global']);
const notificationPermissionSchema = z.enum([
	'org:billing:read',
	'org:users:admin:create',
	'org:billing:update',
	'cluster:optimize:read',
]);
const entitySchema = z.object({id: z.string(), type: z.string()});

function normalizeOptionalEntity(value: unknown) {
	const result = entitySchema.safeParse(value);
	return result.success ? result.data : undefined;
}

function normalizeOptionalPermissions(value: unknown) {
	const result = z.array(notificationPermissionSchema).safeParse(value);
	return result.success ? result.data : undefined;
}

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
	if (!/^https?:\/\//i.test(value)) {
		return undefined;
	}
	try {
		const url = new URL(value);
		return url.username === '' && url.password === '' && (url.protocol === 'http:' || url.protocol === 'https:')
			? value
			: undefined;
	} catch {
		return undefined;
	}
}

const notificationMetaSchema = z.object({
	identifier: optionalStringSchema,
	permissions: z.preprocess(normalizeOptionalPermissions, z.array(notificationPermissionSchema).optional()),
	href: z.preprocess(normalizeHref, z.string().optional()),
	label: optionalStringSchema,
	entity: z.preprocess(normalizeOptionalEntity, entitySchema.optional()),
	parentEntity: z.preprocess(normalizeOptionalEntity, entitySchema.optional()),
	scheduleTs: z.preprocess(
		(value) => (epochMillisecondsSchema.safeParse(value).success ? value : undefined),
		epochMillisecondsSchema.optional(),
	),
});

const optionalMetaSchema = z.preprocess(
	(value) => (typeof value === 'object' && value !== null && !Array.isArray(value) ? value : undefined),
	notificationMetaSchema.optional(),
);

const notificationSchema = z
	.object({
		uuid: z.string().min(1),
		timestamp: epochMillisecondsSchema,
		source: notificationSourceSchema,
		type: notificationTypeSchema,
		title: z.string(),
		description: z.string(),
		state: notificationStateSchema,
		userId: optionalStringSchema,
		orgId: optionalStringSchema,
		meta: optionalMetaSchema,
	})
	.refine(({type, orgId}) => type !== 'org' || orgId !== undefined, {
		message: 'Organization notifications require an organization ID',
		path: ['orgId'],
	});

const notificationFeedSchema = z.array(z.unknown());
const keepAliveSchema = z.object({keepAlive: z.literal(true)});
const notificationSseDataSchema = z.union([keepAliveSchema, notificationSchema]);

type Notification = z.infer<typeof notificationSchema>;

export {keepAliveSchema, notificationFeedSchema, notificationSchema, notificationSseDataSchema};
export type {Notification};

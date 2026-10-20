/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {endpoints} from '#/shared/http/endpoints';

const documentReferenceSchema = z.object({
	'camunda.document.type': z.literal('camunda'),
	storeId: z.string(),
	documentId: z.string(),
	contentHash: z.string().nullable(),
	metadata: z.object({
		contentType: z.string(),
		fileName: z.string(),
		expiresAt: z.string().nullable().optional(),
		size: z.number(),
		processDefinitionId: z.string().nullable().optional(),
		processInstanceKey: z.string().nullable().optional(),
		customProperties: z.record(z.string(), z.unknown()).optional(),
	}),
});
type DocumentReference = z.infer<typeof documentReferenceSchema>;

type DocumentType = 'image' | 'pdf' | 'json' | 'unknown';

type DocumentInfo = {
	fileName: string;
	link: string | null;
	type: DocumentType;
	contentType: string;
	size: number;
	isExpired: boolean;
};

const MIME_TYPE_MAP: Record<string, DocumentType> = {
	'image/jpeg': 'image',
	'image/png': 'image',
	'image/gif': 'image',
	'image/webp': 'image',
	'application/pdf': 'pdf',
	'application/json': 'json',
};

function isDocumentReference(value: unknown): value is DocumentReference {
	return documentReferenceSchema.safeParse(value).success;
}

function toDocumentInfo({documentId, storeId, contentHash, metadata}: DocumentReference): DocumentInfo {
	return {
		link:
			contentHash === null
				? null
				: endpoints.getDocument({documentId: encodeURIComponent(documentId), storeId, contentHash}).url,
		fileName: metadata.fileName,
		type: MIME_TYPE_MAP[metadata.contentType] ?? 'unknown',
		contentType: metadata.contentType,
		size: metadata.size,
		isExpired: metadata.expiresAt ? Date.parse(metadata.expiresAt) < Date.now() : false,
	};
}

export {isDocumentReference, toDocumentInfo};
export type {DocumentInfo};

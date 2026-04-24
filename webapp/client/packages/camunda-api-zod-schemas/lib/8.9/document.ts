/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const documentMetadataSchema = z.object({
	contentType: z.string(),
	fileName: z.string(),
	expiresAt: z.string(),
	size: z.number(),
	processDefinitionId: z.string(),
	processInstanceKey: z.string(),
	customProperties: z.record(z.string(), z.unknown()),
});
type DocumentMetadata = z.infer<typeof documentMetadataSchema>;

const documentReferenceSchema = z.object({
	'camunda.document.type': z.literal('camunda'),
	storeId: z.string(),
	documentId: z.string(),
	contentHash: z.string(),
	metadata: documentMetadataSchema,
});
type DocumentReference = z.infer<typeof documentReferenceSchema>;

const documentCreationFailureDetailSchema = z.object({
	fileName: z.string(),
	status: z.number().int(),
	title: z.string(),
	detail: z.string(),
});
type DocumentCreationFailureDetail = z.infer<typeof documentCreationFailureDetailSchema>;

const createDocumentsResponseBodySchema = z.object({
	createdDocuments: z.array(documentReferenceSchema),
	failedDocuments: z.array(documentCreationFailureDetailSchema),
});
type CreateDocumentsResponseBody = z.infer<typeof createDocumentsResponseBodySchema>;

const documentLinkRequestBodySchema = z.object({
	timeToLive: z.number().optional().default(3600000),
});
type DocumentLinkRequestBody = z.infer<typeof documentLinkRequestBodySchema>;

const documentLinkSchema = z.object({
	url: z.string(),
	expiresAt: z.string(),
});
type DocumentLink = z.infer<typeof documentLinkSchema>;

const getDocumentResponseBodySchema = z.string();
type GetDocumentResponseBody = z.infer<typeof getDocumentResponseBodySchema>;

const createDocument: Endpoint<{
	storeId?: string;
	documentId: string;
}> = {
	method: 'POST',
	getUrl({storeId, documentId}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		if (documentId) {
			searchParams.set('documentId', documentId);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents${query ? `?${query}` : ''}`;
	},
};

const createDocuments: Endpoint<{
	storeId?: string;
}> = {
	method: 'POST',
	getUrl({storeId} = {}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents/batch${query ? `?${query}` : ''}`;
	},
};

const getDocument: Endpoint<{
	documentId: string;
	storeId?: string;
	contentHash?: string;
}> = {
	method: 'GET',
	getUrl({documentId, storeId, contentHash}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		if (contentHash) {
			searchParams.set('contentHash', contentHash);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents/${documentId}${query ? `?${query}` : ''}`;
	},
};

const deleteDocument: Endpoint<{
	documentId: string;
	storeId?: string;
}> = {
	method: 'DELETE',
	getUrl({documentId, storeId}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents/${documentId}${query ? `?${query}` : ''}`;
	},
};

const createDocumentLink: Endpoint<{
	documentId: string;
	storeId?: string;
	contentHash?: string;
}> = {
	method: 'POST',
	getUrl({documentId, storeId, contentHash}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		if (contentHash) {
			searchParams.set('contentHash', contentHash);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents/${documentId}/links${query ? `?${query}` : ''}`;
	},
};

export {
	documentMetadataSchema,
	documentReferenceSchema,
	documentCreationFailureDetailSchema,
	createDocumentsResponseBodySchema,
	documentLinkRequestBodySchema,
	documentLinkSchema,
	getDocumentResponseBodySchema,
	createDocument,
	createDocuments,
	getDocument,
	deleteDocument,
	createDocumentLink,
};
export type {
	DocumentMetadata,
	DocumentReference,
	DocumentCreationFailureDetail,
	CreateDocumentsResponseBody,
	DocumentLinkRequestBody,
	DocumentLink,
	GetDocumentResponseBody,
};

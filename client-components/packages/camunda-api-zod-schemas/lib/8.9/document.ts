/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	documentMetadataSchema,
	documentReferenceSchema,
	documentCreationFailureDetailSchema,
	documentCreationBatchResponseSchema,
	documentLinkRequestSchema,
	documentLinkSchema,
} from './gen';

const docMetadataSchema = documentMetadataSchema;
type DocumentMetadata = z.infer<typeof docMetadataSchema>;

const docReferenceSchema = documentReferenceSchema;
type DocumentReference = z.infer<typeof docReferenceSchema>;

const docCreationFailureDetailSchema = documentCreationFailureDetailSchema;
type DocumentCreationFailureDetail = z.infer<typeof docCreationFailureDetailSchema>;

const createDocumentsResponseBodySchema = documentCreationBatchResponseSchema;
type CreateDocumentsResponseBody = z.infer<typeof createDocumentsResponseBodySchema>;

const documentLinkRequestBodySchema = documentLinkRequestSchema;
type DocumentLinkRequestBody = z.infer<typeof documentLinkRequestBodySchema>;

const docLinkSchema = documentLinkSchema;
type DocumentLink = z.infer<typeof docLinkSchema>;

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
	docMetadataSchema as documentMetadataSchema,
	docReferenceSchema as documentReferenceSchema,
	docCreationFailureDetailSchema as documentCreationFailureDetailSchema,
	createDocumentsResponseBodySchema,
	documentLinkRequestBodySchema,
	docLinkSchema as documentLinkSchema,
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
};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';
import {createDocumentsStatus201Schema} from './gen/zod/createDocumentsSchema';
import {documentCreationFailureDetailSchema as genDocumentCreationFailureDetailSchema} from './gen/zod/documentCreationFailureDetailSchema';
import {documentLinkRequestSchema} from './gen/zod/documentLinkRequestSchema';
import {documentLinkSchema as genDocumentLinkSchema} from './gen/zod/documentLinkSchema';
import {documentMetadataResponseSchema} from './gen/zod/documentMetadataResponseSchema';
import {documentReferenceSchema as genDocumentReferenceSchema} from './gen/zod/documentReferenceSchema';
import type {CreateDocumentsStatus201} from './gen/types/CreateDocuments';
import type {DocumentCreationFailureDetail as GenDocumentCreationFailureDetail} from './gen/types/DocumentCreationFailureDetail';
import type {DocumentLink as GenDocumentLink} from './gen/types/DocumentLink';
import type {DocumentLinkRequest} from './gen/types/DocumentLinkRequest';
import type {DocumentMetadataResponse} from './gen/types/DocumentMetadataResponse';
import type {DocumentReference as GenDocumentReference} from './gen/types/DocumentReference';

// Gen `documentMetadataSchema` is the request metadata. The response metadata is `documentMetadataResponseSchema`.
const documentMetadataSchema = documentMetadataResponseSchema;
type DocumentMetadata = DocumentMetadataResponse;

const documentReferenceSchema = genDocumentReferenceSchema;
type DocumentReference = GenDocumentReference;

const documentCreationFailureDetailSchema = genDocumentCreationFailureDetailSchema;
type DocumentCreationFailureDetail = GenDocumentCreationFailureDetail;

const createDocumentsResponseBodySchema = createDocumentsStatus201Schema;
type CreateDocumentsResponseBody = CreateDocumentsStatus201;

const documentLinkRequestBodySchema = documentLinkRequestSchema;
type DocumentLinkRequestBody = DocumentLinkRequest;

const documentLinkSchema = genDocumentLinkSchema;
type DocumentLink = GenDocumentLink;

// Kept manual: the spec returns binary content (`File`/`Blob`), and the consumers read it as text.
const getDocumentResponseBodySchema = z.string();
type GetDocumentResponseBody = z.infer<typeof getDocumentResponseBodySchema>;

const createDocument = {
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
		return `/${API_VERSION}/documents${query ? `?${query}` : ''}` as const;
	},
} as const satisfies Endpoint<{
	storeId?: string;
	documentId: string;
}>;

const createDocuments = {
	method: 'POST',
	getUrl({storeId} = {}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents/batch${query ? `?${query}` : ''}` as const;
	},
} as const satisfies Endpoint<{
	storeId?: string;
}>;

const getDocument = {
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
		return `/${API_VERSION}/documents/${documentId}${query ? `?${query}` : ''}` as const;
	},
} as const satisfies Endpoint<{
	documentId: string;
	storeId?: string;
	contentHash?: string;
}>;

const deleteDocument = {
	method: 'DELETE',
	getUrl({documentId, storeId}) {
		const searchParams = new URLSearchParams();
		if (storeId) {
			searchParams.set('storeId', storeId);
		}
		const query = searchParams.toString();
		return `/${API_VERSION}/documents/${documentId}${query ? `?${query}` : ''}` as const;
	},
} as const satisfies Endpoint<{
	documentId: string;
	storeId?: string;
}>;

const createDocumentLink = {
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
		return `/${API_VERSION}/documents/${documentId}/links${query ? `?${query}` : ''}` as const;
	},
} as const satisfies Endpoint<{
	documentId: string;
	storeId?: string;
	contentHash?: string;
}>;

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

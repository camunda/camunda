/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.11';

function createDocumentReference(
	overrides: Partial<Omit<DocumentReference, 'metadata'>> & {metadata?: Partial<DocumentReference['metadata']>} = {},
): DocumentReference {
	const {metadata, ...reference} = overrides;
	return {
		'camunda.document.type': 'camunda',
		storeId: 'in-memory',
		documentId: 'doc-abc',
		contentHash: 'sha256-xyz',
		...reference,
		metadata: {
			contentType: 'application/pdf',
			fileName: 'test-document.pdf',
			expiresAt: null,
			size: 1024,
			processDefinitionId: null,
			processInstanceKey: null,
			customProperties: {},
			...metadata,
		},
	};
}

export {createDocumentReference};

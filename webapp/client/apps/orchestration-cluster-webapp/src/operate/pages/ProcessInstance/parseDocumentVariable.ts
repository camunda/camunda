/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {untruncateJson} from '#/operate/shared/Editors/untruncateJson';
import {isDocumentReference, toDocumentInfo, type DocumentInfo} from '#/operate/shared/Documents/documentInfo';

type DocumentParseResult =
	{type: 'single'; document: DocumentInfo} | {type: 'list'; documents: DocumentInfo[]; isLowerBound: boolean};

function safeJsonParse(value: string): unknown {
	try {
		return JSON.parse(value);
	} catch {
		return undefined;
	}
}

function parseDocumentVariable(value: string, isTruncated: boolean): DocumentParseResult | null {
	const {completed, collectionDepth} = isTruncated ? untruncateJson(value) : {completed: value, collectionDepth: 0};
	const parsed = safeJsonParse(completed);

	if (isDocumentReference(parsed)) {
		return {type: 'single', document: toDocumentInfo(parsed)};
	}

	if (!Array.isArray(parsed) || parsed.length === 0) {
		return null;
	}

	const documents = parsed.filter(isDocumentReference);
	if (documents.length === 0 || (!isTruncated && documents.length !== parsed.length)) {
		return null;
	}

	if (documents.length === 1 && !isTruncated) {
		return {type: 'single', document: toDocumentInfo(documents[0]!)};
	}

	return {
		type: 'list',
		documents: documents.map(toDocumentInfo),
		isLowerBound: collectionDepth > 0 || documents.length < parsed.length,
	};
}

export {parseDocumentVariable};
export type {DocumentParseResult};

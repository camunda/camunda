/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient, type QueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {getDocumentReferences} from '@camunda/case-management-sdk';
import type {
	CreateDocumentsResponseBody,
	DocumentReference,
	ProcessInstance,
	QueryVariablesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {toast} from '@camunda/design-system';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {request} from '#/shared/http/request';
import {waitUntilReady} from '#/shared/http/waitUntilReady';
import {
	getCaseVariablesRequestBody,
	getDocumentVariablesRequestBody,
} from '#/tasklist/modules/case-details/getCaseRequestBodies';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';

/** The root-scope variable that collects the documents that case workers upload on the case page. */
const CASE_DOCUMENTS_VARIABLE = 'caseDocuments';

type CaseRef = Pick<ProcessInstance, 'processInstanceKey' | 'processDefinitionId'>;

type CaseDocumentsUpload = {
	createdDocuments: DocumentReference[];
	failedFileNames: string[];
	/** Whether the document variable search returns the new documents yet. */
	isVisible: boolean;
};

/** Stores the files in the document store, linked to the case in their metadata. */
async function createCaseDocuments({processInstanceKey, processDefinitionId}: CaseRef, files: File[]) {
	const {response, error} = await request(
		endpoints.createDocuments({
			documentsPayload: new Map([
				[CASE_DOCUMENTS_VARIABLE, {files, metadata: {processInstanceKey, processDefinitionId}}],
			]),
		}),
	);

	if (error !== null) {
		throw error;
	}

	// 201, and 207 when some files fail, share the same body.
	return (await response.json()) as CreateDocumentsResponseBody;
}

/**
 * Reads the documents that the case variable holds: a list of document references, or a single one.
 * The search is eventually consistent, so an upload by another case worker a moment ago can be missing,
 * and the next write then drops it.
 */
async function getCaseDocumentsValue(processInstanceKey: string): Promise<unknown[]> {
	const {response, error} = await request(
		endpoints.queryVariables(getCaseVariablesRequestBody(processInstanceKey, [CASE_DOCUMENTS_VARIABLE]), {
			truncateValues: false,
		}),
	);

	if (error !== null) {
		throw error;
	}

	const {items} = (await response.json()) as QueryVariablesResponseBody;
	const [variable] = items;
	const value = variable === undefined ? null : tryParseJSON(variable.value);

	if (value === null) {
		return [];
	}

	return Array.isArray(value) ? value : [value];
}

/**
 * Uploads the files and appends their document references to the case variable in the root scope, so that the
 * process can use them and the case page lists them. Then waits until the search returns them.
 */
async function uploadCaseDocuments(
	queryClient: QueryClient,
	caseRef: CaseRef,
	files: File[],
): Promise<CaseDocumentsUpload> {
	const {createdDocuments, failedDocuments} = await createCaseDocuments(caseRef, files);

	if (createdDocuments.length === 0) {
		throw new Error('No document was uploaded');
	}

	const {processInstanceKey} = caseRef;
	const documents = [...(await getCaseDocumentsValue(processInstanceKey)), ...createdDocuments];
	const {error} = await request(
		endpoints.updateElementInstanceVariables(processInstanceKey, {
			variables: {[CASE_DOCUMENTS_VARIABLE]: documents},
			local: true,
		}),
	);

	if (error !== null) {
		throw error;
	}

	const documentIds = createdDocuments.map(({documentId}) => documentId);
	const result = await waitUntilReady(
		queryClient,
		queries.queryVariables(getDocumentVariablesRequestBody(processInstanceKey), {truncateValues: false}),
		{
			isReady: ({items}) => {
				const shownIds = new Set(getDocumentReferences(items).map(({documentId}) => documentId));
				return documentIds.every((documentId) => shownIds.has(documentId));
			},
		},
	);

	return {
		createdDocuments,
		failedFileNames: failedDocuments.map(({fileName}) => fileName),
		isVisible: result !== undefined,
	};
}

function useUploadCaseDocuments(caseRef: CaseRef) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (files: File[]) => uploadCaseDocuments(queryClient, caseRef, files),
		onSuccess: ({createdDocuments, failedFileNames, isVisible}) => {
			const count = createdDocuments.length;

			if (failedFileNames.length > 0) {
				toast.error(t('tasklist.caseDocumentsPartiallyUploaded', {fileNames: failedFileNames.join(', ')}));
			} else if (isVisible) {
				toast.success(t('tasklist.caseDocumentsUploaded', {count}));
			} else {
				toast.info(t('tasklist.caseDocumentsUploadedNotVisibleYet', {count}));
			}
		},
		onError: () => {
			toast.error(t('tasklist.caseDocumentsUploadFailed'));
		},
	});
}

export {useUploadCaseDocuments};

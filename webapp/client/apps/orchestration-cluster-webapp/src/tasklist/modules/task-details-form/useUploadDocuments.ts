/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import type {CreateDocumentsResponseBody, DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';

const MIXED_SUCCESS_AND_FAILED_DOCUMENTS_STATUS_CODE = 207;
const PICKER_KEY = 'pickerKey';

function useUploadDocuments() {
	return useMutation({
		mutationFn: async (files: Map<string, File[]>) => {
			const filesWithMetadata = new Map(
				Array.from(files.entries()).map(([key, files]) => [
					key,
					{
						files,
						metadata: {
							customProperties: {
								[PICKER_KEY]: key,
							},
						},
					},
				]),
			);
			const {response, error} = await request(endpoints.createDocuments({documentsPayload: filesWithMetadata}));

			if (error !== null) {
				throw error;
			}

			if (response.status === MIXED_SUCCESS_AND_FAILED_DOCUMENTS_STATUS_CODE) {
				throw new Error('Failed to upload some documents');
			}

			const payload = (await response.json()) as CreateDocumentsResponseBody;
			const result = new Map<string, DocumentReference[]>();

			for (const document of payload.createdDocuments) {
				const pickerKey = document.metadata.customProperties?.[PICKER_KEY];

				if (typeof pickerKey !== 'string' || pickerKey.length === 0) {
					continue;
				}

				const documentResult = result.get(pickerKey) ?? [];
				documentResult.push(document);
				result.set(pickerKey, documentResult);
			}

			return result;
		},
	});
}

export {useUploadDocuments};

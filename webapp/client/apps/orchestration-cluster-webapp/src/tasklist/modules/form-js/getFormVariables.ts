/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.11';
import set from 'lodash/set';
import {extractFilePath} from './extractFilePath';
import {mergeVariables} from './mergeVariables';

type PartialVariable = {
	name: string;
	value: string;
};

function injectFileMetadataIntoData(options: {
	data: Record<string, unknown>;
	fileMetadata: Map<string, DocumentReference[]>;
	pathsToInject: Map<string, string>;
}): Record<string, unknown> {
	const {data, fileMetadata, pathsToInject} = options;
	let result = structuredClone(data);

	pathsToInject.forEach((filepickerPath, fileKey) => {
		const metadata = fileMetadata.get(fileKey);

		if (metadata === undefined) {
			return;
		}

		result = set(result, filepickerPath, metadata);
	});

	return result;
}

/** Uploads the picked files and turns the form data into the variables to send. */
async function getFormVariables({
	initialData,
	data,
	files,
	handleFileUpload,
}: {
	initialData: Record<string, unknown>;
	data: Record<string, unknown>;
	files: Map<string, File[]>;
	handleFileUpload: (files: Map<string, File[]>) => Promise<Map<string, DocumentReference[]>>;
}): Promise<PartialVariable[]> {
	const enrichedData =
		files.size === 0
			? data
			: injectFileMetadataIntoData({
					data,
					fileMetadata: await handleFileUpload(files),
					pathsToInject: extractFilePath(data),
				});

	return Object.entries(mergeVariables(initialData, enrichedData)).map(([name, value]) => ({
		name,
		value: JSON.stringify(value),
	}));
}

export {getFormVariables};
export type {PartialVariable};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {documentReferenceSchema, type DocumentReference, type Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';

type CaseDocument = {
	id: string;
	variableName: string;
	document: DocumentReference;
};

function getCaseDocuments(variables: Variable[]): CaseDocument[] {
	return variables.flatMap(({name, value}) => {
		const parsedValue = tryParseJSON(value);
		const candidates = Array.isArray(parsedValue) ? parsedValue : [parsedValue];

		return candidates.flatMap((candidate) => {
			const result = documentReferenceSchema.safeParse(candidate);

			return result.success
				? [{id: `${name}-${result.data.documentId}`, variableName: name, document: result.data}]
				: [];
		});
	});
}

export {getCaseDocuments};
export type {CaseDocument};

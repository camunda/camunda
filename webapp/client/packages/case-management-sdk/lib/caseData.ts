/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CaseModel} from './parseCaseModel';

const CASE_HEADER_VARIABLE = 'caseHeader';
const VARIABLE_REFERENCE = /^=\s*([A-Za-z_][A-Za-z0-9_]*)\s*$/;

/** Returns the variable that an expression such as `=caseId` reads, or `null` for any other expression. */
function getVariableReference(expression: string | null): string | null {
	return expression === null ? null : (VARIABLE_REFERENCE.exec(expression)?.[1] ?? null);
}

/**
 * The root-scope variables that a case application reads, taken from the BPMN: the title variable of the
 * case marker, the case header, and the correlation keys of "Stage done". A case application requests
 * these names only, not all variables of the case.
 */
function getCaseVariableNames(model: CaseModel): string[] {
	const names = new Set([CASE_HEADER_VARIABLE]);

	if (model.caseMarker?.titleVariable) {
		names.add(model.caseMarker.titleVariable);
	}

	for (const {manualCompletion} of model.stages) {
		const correlationVariable = getVariableReference(manualCompletion?.correlationKey ?? null);

		if (correlationVariable !== null) {
			names.add(correlationVariable);
		}
	}

	return [...names];
}

type CaseHeaderEntry = {
	label: string;
	value: string;
};

type VariableLike = {
	name: string;
	/** The JSON-serialized variable value, as the variable search returns it. */
	value: string;
};

type DocumentReference = {
	variableName: string;
	documentId: string;
	storeId: string | null;
	contentHash: string | null;
	fileName: string;
	contentType: string | null;
	size: number | null;
};

function parseJSON(value: string): unknown {
	try {
		return JSON.parse(value);
	} catch {
		return undefined;
	}
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toText(value: unknown): string | null {
	if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
		return String(value);
	}

	return value === null ? '' : null;
}

/** Reads the `caseHeader: {label, value}[]` variable (CASE_DATA.md). Returns `null` for a wrong shape. */
function getCaseHeader(variable: VariableLike | undefined): CaseHeaderEntry[] | null {
	const value = variable === undefined ? undefined : parseJSON(variable.value);

	if (!Array.isArray(value)) {
		return null;
	}

	const entries = value.flatMap((entry) => {
		const label = isRecord(entry) ? toText(entry['label']) : null;
		const text = isRecord(entry) ? toText(entry['value']) : null;

		return label === null || text === null ? [] : [{label, value: text}];
	});

	return entries.length === value.length ? entries : null;
}

/** Reads the case title from the variable that the case marker names (CASE_DATA.md). */
function getCaseTitle(variable: VariableLike | undefined): string | null {
	const value = variable === undefined ? undefined : parseJSON(variable.value);
	const title = toText(value);

	return title === null || title.trim() === '' ? null : title;
}

function toDocumentReference(variableName: string, value: unknown): DocumentReference | null {
	if (!isRecord(value) || value['camunda.document.type'] !== 'camunda' || typeof value['documentId'] !== 'string') {
		return null;
	}

	const metadata = isRecord(value['metadata']) ? value['metadata'] : {};

	return {
		variableName,
		documentId: value['documentId'],
		storeId: typeof value['storeId'] === 'string' ? value['storeId'] : null,
		contentHash: typeof value['contentHash'] === 'string' ? value['contentHash'] : null,
		fileName: typeof metadata['fileName'] === 'string' ? metadata['fileName'] : value['documentId'],
		contentType: typeof metadata['contentType'] === 'string' ? metadata['contentType'] : null,
		size: typeof metadata['size'] === 'number' ? metadata['size'] : null,
	};
}

/** Finds the document references in variable values: one reference, or a list of them (CASE_UI.md). */
function getDocumentReferences(variables: VariableLike[]): DocumentReference[] {
	return variables.flatMap(({name, value}) => {
		const parsed = parseJSON(value);
		const candidates = Array.isArray(parsed) ? parsed : [parsed];

		return candidates.flatMap((candidate) => toDocumentReference(name, candidate) ?? []);
	});
}

export {
	CASE_HEADER_VARIABLE,
	getCaseHeader,
	getCaseTitle,
	getCaseVariableNames,
	getDocumentReferences,
	getVariableReference,
};
export type {CaseHeaderEntry, DocumentReference, VariableLike};

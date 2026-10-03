/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';

const METADATA_VARIABLE_NAME = 'camunda_metadata';

const caseMetadataSchema = z.object({
	case: z.object({
		formId: z.string().min(1).optional(),
		header: z
			.array(
				z.object({
					label: z.string(),
					value: z.union([z.string(), z.number(), z.boolean()]).transform(String),
				}),
			)
			.default([]),
	}),
});

type CaseMetadata = z.infer<typeof caseMetadataSchema>['case'];

function parseCaseMetadata(variable: Variable | undefined): CaseMetadata | null {
	if (variable === undefined) {
		return null;
	}

	const result = caseMetadataSchema.safeParse(tryParseJSON(variable.value));

	return result.success ? result.data.case : null;
}

export {METADATA_VARIABLE_NAME, parseCaseMetadata};
export type {CaseMetadata};

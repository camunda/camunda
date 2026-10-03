/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const PROGRESS_VIEWS = ['stages', 'diagram'] as const;

type ProgressView = (typeof PROGRESS_VIEWS)[number];

function isProgressView(value: string): value is ProgressView {
	// casting is necessary for the type guard to work
	return (PROGRESS_VIEWS as readonly string[]).includes(value);
}

const caseDetailsSearchSchema = z.object({
	progressView: z.enum(PROGRESS_VIEWS).default('stages'),
});

type CaseDetailsSearch = z.infer<typeof caseDetailsSearchSchema>;

const caseDetailsSearchDefaults = {
	progressView: 'stages',
} as const satisfies CaseDetailsSearch;

export {caseDetailsSearchDefaults, caseDetailsSearchSchema, isProgressView};
export type {ProgressView};

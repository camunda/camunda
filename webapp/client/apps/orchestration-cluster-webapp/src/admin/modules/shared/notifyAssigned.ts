/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';
import {toast} from '@camunda/design-system';
import {getErrorMessage} from '#/shared/http/getErrorMessage';

type Params = {
	t: TFunction;
	scope: string;
	assigned: number;
	errors: unknown[];
	single: string;
};

// Resolves to the id of the success toast, which a failed reconciliation supersedes with its own
// warning. A partial-failure warning carries information that warning lacks, so it has no id.
async function notifyAssigned({t, scope, assigned, errors, single}: Params): Promise<string | number | undefined> {
	if (errors.length === 0) {
		return toast.success(assigned === 1 ? single : t(`${scope}.assignedMany`, {count: assigned}));
	}
	toast.warning(t(`${scope}.assignedPartial`, {assigned, total: assigned + errors.length}), {
		description: await getErrorMessage(errors[0]),
	});
	return undefined;
}

// `toast.dismiss(undefined)` would close every toast on screen, hence the guard.
async function dismissSuperseded(summary: Promise<string | number | undefined> | undefined) {
	const id = await summary;
	if (id !== undefined) {
		toast.dismiss(id);
	}
}

export {dismissSuperseded, notifyAssigned};

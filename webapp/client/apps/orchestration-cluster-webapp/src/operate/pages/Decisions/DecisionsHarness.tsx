/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearch} from '@tanstack/react-router';
import {Decisions} from './Decisions';

// Test-only wrapper: the ad-hoc test route has no validateSearch, so mirror the
// real route schema's coercions here — numeric-looking values arrive as numbers
// from the default search parser and the state booleans have defaults.
function toOptionalString(value: unknown) {
	return value === undefined ? undefined : String(value);
}

function toOptionalNumber(value: unknown) {
	if (value === undefined) {
		return undefined;
	}

	const number = Number(value);
	return Number.isFinite(number) ? number : undefined;
}

function DecisionsHarness({
	renderSelectedVersion = true,
	renderInstances = true,
}: {
	renderSelectedVersion?: boolean;
	renderInstances?: boolean;
}) {
	const search = useSearch({strict: false}) as Record<string, unknown>;

	return (
		// The app shell gives #app a viewport height; without it the filters
		// panel (absolutely positioned content) collapses to zero height.
		<div style={{height: '100vh'}}>
			<Decisions
				decisionDefinitionId={toOptionalString(search.decisionDefinitionId)}
				decisionDefinitionVersion={
					renderSelectedVersion ? toOptionalNumber(search.decisionDefinitionVersion) : undefined
				}
				tenantId={toOptionalString(search.tenantId)}
				evaluated={renderInstances && (search.evaluated === undefined ? true : Boolean(search.evaluated))}
				failed={renderInstances && (search.failed === undefined ? true : Boolean(search.failed))}
				decisionEvaluationInstanceKey={toOptionalString(search.decisionEvaluationInstanceKey)}
				processInstanceKey={toOptionalString(search.processInstanceKey)}
				businessId={toOptionalString(search.businessId)}
				evaluationDateFrom={toOptionalString(search.evaluationDateFrom)}
				evaluationDateTo={toOptionalString(search.evaluationDateTo)}
				sort={toOptionalString(search.sort)}
			/>
		</div>
	);
}

function DecisionsNavigationHarness() {
	return <DecisionsHarness renderSelectedVersion={false} />;
}

function DecisionsWithoutInstancesHarness() {
	return <DecisionsHarness renderInstances={false} />;
}

function DecisionsNavigationWithoutInstancesHarness() {
	return <DecisionsHarness renderInstances={false} renderSelectedVersion={false} />;
}

export {
	DecisionsHarness,
	DecisionsNavigationHarness,
	DecisionsWithoutInstancesHarness,
	DecisionsNavigationWithoutInstancesHarness,
};

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

function DecisionsHarness() {
	const search = useSearch({strict: false}) as Record<string, unknown>;

	return (
		// The app shell gives #app a viewport height; without it the filters
		// panel (absolutely positioned content) collapses to zero height.
		<div style={{height: '100vh'}}>
			<Decisions
				decisionDefinitionId={toOptionalString(search.decisionDefinitionId)}
				decisionDefinitionVersion={
					typeof search.decisionDefinitionVersion === 'number' ? search.decisionDefinitionVersion : undefined
				}
				tenantId={toOptionalString(search.tenantId)}
				evaluated={search.evaluated === undefined ? true : Boolean(search.evaluated)}
				failed={search.failed === undefined ? true : Boolean(search.failed)}
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

export {DecisionsHarness};

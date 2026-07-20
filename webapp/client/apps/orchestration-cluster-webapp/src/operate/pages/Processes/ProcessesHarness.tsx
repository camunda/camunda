/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearch} from '@tanstack/react-router';
import {Processes} from './Processes';

// Test-only wrapper: the ad-hoc test route has no validateSearch, so mirror the
// real route schema's coercions here — numeric-looking values arrive as numbers
// from the default search parser and the state booleans have defaults.
function toOptionalString(value: unknown) {
	return value === undefined ? undefined : String(value);
}

function ProcessesHarness() {
	const search = useSearch({strict: false}) as Record<string, unknown>;

	return (
		// The app shell gives #app a viewport height; without it the filters
		// panel (absolutely positioned content) collapses to zero height.
		<div style={{height: '100vh'}}>
			<Processes
				process={toOptionalString(search.process)}
				version={typeof search.version === 'number' ? search.version : undefined}
				elementId={toOptionalString(search.elementId)}
				active={search.active === undefined ? true : Boolean(search.active)}
				incidents={search.incidents === undefined ? true : Boolean(search.incidents)}
				completed={Boolean(search.completed)}
				canceled={Boolean(search.canceled)}
				tenantId={toOptionalString(search.tenantId)}
				processInstanceKey={toOptionalString(search.processInstanceKey)}
				parentProcessInstanceKey={toOptionalString(search.parentProcessInstanceKey)}
				businessId={toOptionalString(search.businessId)}
				batchOperationKey={toOptionalString(search.batchOperationKey)}
				errorMessage={toOptionalString(search.errorMessage)}
				hasRetriesLeft={search.hasRetriesLeft === undefined ? undefined : Boolean(search.hasRetriesLeft)}
				startDateFrom={toOptionalString(search.startDateFrom)}
				startDateTo={toOptionalString(search.startDateTo)}
				endDateFrom={toOptionalString(search.endDateFrom)}
				endDateTo={toOptionalString(search.endDateTo)}
			/>
		</div>
	);
}

export {ProcessesHarness};

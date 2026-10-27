/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearch} from '@tanstack/react-router';
import {Decisions} from './Decisions';
import {validateDecisionsSearch} from './decisionsSearch';

function DecisionsHarness() {
	const search = validateDecisionsSearch(useSearch({strict: false}));

	return (
		// The app shell gives #app a viewport height; without it the filters
		// panel (absolutely positioned content) collapses to zero height.
		<div style={{height: '100vh'}}>
			<Decisions {...search} />
		</div>
	);
}

export {DecisionsHarness};

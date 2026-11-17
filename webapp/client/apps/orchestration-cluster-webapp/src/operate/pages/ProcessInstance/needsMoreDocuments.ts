/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VARIABLES_PAGE_SIZE} from './variables.queries';

function needsMoreDocuments(documentCountsPerPage: number[]): boolean {
	const total = documentCountsPerPage.reduce((sum, count) => sum + count, 0);
	return total < VARIABLES_PAGE_SIZE || documentCountsPerPage.at(-1) === 0;
}

export {needsMoreDocuments};

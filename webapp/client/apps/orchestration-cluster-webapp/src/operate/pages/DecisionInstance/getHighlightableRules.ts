/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function getHighlightableRules(matchedRules: GetDecisionInstanceResponseBody['matchedRules'] | undefined) {
	if (matchedRules === undefined) {
		return [];
	}

	return Array.from(new Set(matchedRules.flatMap(({ruleIndex}) => (ruleIndex === null ? [] : [ruleIndex]))));
}

export {getHighlightableRules};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {getHighlightableRules} from './getHighlightableRules';

function createMatchedRules(ruleIndexes: number[]): GetDecisionInstanceResponseBody['matchedRules'] {
	return ruleIndexes.map((ruleIndex) => ({
		ruleIndex,
		ruleId: `rule-${ruleIndex}`,
		evaluatedOutputs: [],
	}));
}

describe('getHighlightableRules', () => {
	it('should return an empty array when matched rules are undefined', () => {
		expect(getHighlightableRules(undefined)).toEqual([]);
	});

	it('should return an empty array when matched rules are empty', () => {
		expect(getHighlightableRules([])).toEqual([]);
	});

	it('should return distinct rule indexes in their original order', () => {
		expect(getHighlightableRules(createMatchedRules([3, 1, 3, 2, 1, 2]))).toEqual([3, 1, 2]);
	});
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {applyInstanceSelection, mapDecisionInstancesSort} from './decisionsFilter';

describe('mapDecisionInstancesSort', () => {
	it('defaults to evaluation date descending when no sort param is given', () => {
		expect(mapDecisionInstancesSort(undefined)).toEqual([{field: 'evaluationDate', order: 'DESC'}]);
	});

	it('parses a valid field and order', () => {
		expect(mapDecisionInstancesSort('businessId+ASC')).toEqual([{field: 'businessId', order: 'ASC'}]);
	});

	it('falls back to the default for an unrecognized field', () => {
		expect(mapDecisionInstancesSort('foo+ASC')).toEqual([{field: 'evaluationDate', order: 'DESC'}]);
	});

	it('falls back to the default for an unrecognized order', () => {
		expect(mapDecisionInstancesSort('businessId+bar')).toEqual([{field: 'evaluationDate', order: 'DESC'}]);
	});

	it('falls back to the default when the order is missing', () => {
		expect(mapDecisionInstancesSort('businessId')).toEqual([{field: 'evaluationDate', order: 'DESC'}]);
	});
});

describe('applyInstanceSelection', () => {
	const filter = {state: {$in: ['EVALUATED']}, decisionEvaluationInstanceKey: {$in: ['1', '2', '3']}} as never;

	it('keeps the filter untouched without a selection', () => {
		expect(applyInstanceSelection(filter, [], [])).toBe(filter);
	});

	it('keeps an active instance-key filter when rows are excluded', () => {
		expect(applyInstanceSelection(filter, [], ['1'])).toEqual({
			state: {$in: ['EVALUATED']},
			decisionEvaluationInstanceKey: {$in: ['1', '2', '3'], $notIn: ['1']},
		});
	});

	it('limits included rows to the active instance-key filter', () => {
		expect(applyInstanceSelection(filter, ['2', '9'], [])).toEqual({
			state: {$in: ['EVALUATED']},
			decisionEvaluationInstanceKey: {$in: ['2']},
		});
	});

	it('never produces an empty $in when included rows are outside the active filter', () => {
		expect(applyInstanceSelection(filter, ['9'], [])).toEqual({
			state: {$in: ['EVALUATED']},
			decisionEvaluationInstanceKey: {$in: ['-1']},
		});
	});
});

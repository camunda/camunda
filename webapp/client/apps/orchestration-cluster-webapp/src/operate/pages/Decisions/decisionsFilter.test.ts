/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {mapDecisionInstancesSort} from './decisionsFilter';

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

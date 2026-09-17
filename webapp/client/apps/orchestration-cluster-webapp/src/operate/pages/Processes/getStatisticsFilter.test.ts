/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {getStatisticsFilter} from './getStatisticsFilter';

const NONE = {active: false, incidents: false, completed: false, canceled: false, suspended: false};

describe('getStatisticsFilter', () => {
	it('returns undefined when no state is selected', () => {
		expect(getStatisticsFilter(NONE)).toBeUndefined();
	});

	it('filters to active instances without an incident when only active is selected', () => {
		expect(getStatisticsFilter({...NONE, active: true})).toEqual({
			state: {$eq: 'ACTIVE'},
			hasIncident: false,
		});
	});

	it('does not scope incidents to a state when only incidents is selected', () => {
		expect(getStatisticsFilter({...NONE, incidents: true})).toEqual({hasIncident: true});
	});

	it('combines the state filter with an incidents-in-any-state clause when active and incidents are both selected', () => {
		expect(getStatisticsFilter({...NONE, active: true, incidents: true})).toEqual({
			$or: [{state: {$eq: 'ACTIVE'}}, {hasIncident: true}],
		});
	});

	it('uses $in across multiple selected states', () => {
		expect(getStatisticsFilter({...NONE, active: true, completed: true, canceled: true})).toEqual({
			state: {$in: ['ACTIVE', 'COMPLETED', 'TERMINATED']},
			hasIncident: false,
		});
	});

	it('combines multiple states with an incidents-in-any-state clause', () => {
		expect(getStatisticsFilter({...NONE, completed: true, incidents: true})).toEqual({
			$or: [{state: {$eq: 'COMPLETED'}}, {hasIncident: true}],
		});
	});

	it('filters to suspended instances alone', () => {
		expect(getStatisticsFilter({...NONE, suspended: true})).toEqual({state: {$eq: 'SUSPENDED'}});
	});

	it('combines suspended with a selected state as separate branches', () => {
		expect(getStatisticsFilter({...NONE, active: true, suspended: true})).toEqual({
			$or: [{state: {$eq: 'ACTIVE'}, hasIncident: false}, {state: {$eq: 'SUSPENDED'}}],
		});
	});

	it('excludes suspended instances from the incidents branch to avoid asserting two states at once', () => {
		expect(getStatisticsFilter({...NONE, incidents: true, suspended: true})).toEqual({
			$or: [{state: {$eq: 'SUSPENDED'}}, {hasIncident: true, state: {$neq: 'SUSPENDED'}}],
		});
	});
});

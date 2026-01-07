#!/usr/bin/env node

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Test to verify JSON serialization of query requests with actor filters/sorts
 */

import {queryBatchOperationsRequestBodySchema} from './dist/8.8/batch-operation.js';

console.log('Testing JSON serialization of query requests...\n');

// Test 1: Query with actor filters
const queryWithActorFilter = {
	filter: {
		actorId: {$eq: 'user-123'},
		actorType: 'USER',
	},
};

const parsedQuery1 = queryBatchOperationsRequestBodySchema.parse(queryWithActorFilter);
const json1 = JSON.stringify(parsedQuery1, null, 2);
console.log('Query with actor filter:');
console.log(json1);
console.log();

// Test 2: Query with actor sort
const queryWithActorSort = {
	sort: [
		{field: 'actorId', order: 'asc'},
		{field: 'actorType', order: 'desc'},
	],
};

const parsedQuery2 = queryBatchOperationsRequestBodySchema.parse(queryWithActorSort);
const json2 = JSON.stringify(parsedQuery2, null, 2);
console.log('Query with actor sort:');
console.log(json2);
console.log();

// Test 3: Combined query
const combinedQuery = {
	filter: {
		actorId: 'user-456',
		actorType: {$in: ['USER', 'CLIENT']},
		state: 'ACTIVE',
	},
	sort: [
		{field: 'actorType', order: 'asc'},
		{field: 'startDate', order: 'desc'},
	],
	page: {
		from: 0,
		limit: 20,
	},
};

const parsedQuery3 = queryBatchOperationsRequestBodySchema.parse(combinedQuery);
const json3 = JSON.stringify(parsedQuery3, null, 2);
console.log('Combined query with actor filter and sort:');
console.log(json3);

console.log('\n✓ All serialization tests passed!');

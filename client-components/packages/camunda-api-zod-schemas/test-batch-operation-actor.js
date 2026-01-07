#!/usr/bin/env node

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Manual test for batch operation actor support
 * 
 * Run with: node test-batch-operation-actor.js
 * 
 * This test validates:
 * 1. Response parsing with actorId and actorType present
 * 2. Response parsing with actorId and actorType null/missing
 * 3. Query filtering by actorId and actorType
 * 4. Query sorting by actorId and actorType
 */

import {
	batchOperationSchema,
	batchOperationActorTypeSchema,
	queryBatchOperationsRequestBodySchema,
} from './dist/8.8/batch-operation.js';

console.log('Testing batch operation actor support...\n');

let passCount = 0;
let failCount = 0;

function test(name, fn) {
	try {
		fn();
		console.log(`✓ ${name}`);
		passCount++;
	} catch (error) {
		console.error(`✗ ${name}`);
		console.error(`  Error: ${error.message}`);
		failCount++;
	}
}

// Test 1: Response parsing with actorId and actorType present
test('Response with actorId and actorType', () => {
	const response = {
		batchOperationKey: '123',
		state: 'ACTIVE',
		batchOperationType: 'CANCEL_PROCESS_INSTANCE',
		operationsTotalCount: 10,
		operationsFailedCount: 0,
		operationsCompletedCount: 5,
		actorId: 'user-123',
		actorType: 'USER',
	};
	
	const result = batchOperationSchema.parse(response);
	if (result.actorId !== 'user-123') {
		throw new Error('actorId not parsed correctly');
	}
	if (result.actorType !== 'USER') {
		throw new Error('actorType not parsed correctly');
	}
});

// Test 2: Response parsing with actorId and actorType as null
test('Response with null actorId and actorType', () => {
	const response = {
		batchOperationKey: '123',
		state: 'COMPLETED',
		batchOperationType: 'RESOLVE_INCIDENT',
		operationsTotalCount: 10,
		operationsFailedCount: 0,
		operationsCompletedCount: 10,
		actorId: null,
		actorType: null,
	};
	
	const result = batchOperationSchema.parse(response);
	if (result.actorId !== null) {
		throw new Error('actorId should be null');
	}
	if (result.actorType !== null) {
		throw new Error('actorType should be null');
	}
});

// Test 3: Response parsing with actorId and actorType missing
test('Response with missing actorId and actorType', () => {
	const response = {
		batchOperationKey: '456',
		state: 'FAILED',
		batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
		operationsTotalCount: 5,
		operationsFailedCount: 5,
		operationsCompletedCount: 0,
	};
	
	const result = batchOperationSchema.parse(response);
	if (result.actorId !== undefined) {
		throw new Error('actorId should be undefined when missing');
	}
	if (result.actorType !== undefined) {
		throw new Error('actorType should be undefined when missing');
	}
});

// Test 4: Actor type enum validation - USER
test('Actor type enum - USER', () => {
	const result = batchOperationActorTypeSchema.parse('USER');
	if (result !== 'USER') {
		throw new Error('USER actor type not parsed correctly');
	}
});

// Test 5: Actor type enum validation - CLIENT
test('Actor type enum - CLIENT', () => {
	const result = batchOperationActorTypeSchema.parse('CLIENT');
	if (result !== 'CLIENT') {
		throw new Error('CLIENT actor type not parsed correctly');
	}
});

// Test 6: Query filtering by actorId with string value
test('Query filter with actorId string', () => {
	const query = {
		filter: {
			actorId: 'user-123',
		},
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (result.filter?.actorId !== 'user-123') {
		throw new Error('actorId filter not parsed correctly');
	}
});

// Test 7: Query filtering by actorId with advanced filter
test('Query filter with actorId advanced filter', () => {
	const query = {
		filter: {
			actorId: {
				$eq: 'user-123',
			},
		},
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (typeof result.filter?.actorId !== 'object' || result.filter.actorId.$eq !== 'user-123') {
		throw new Error('actorId advanced filter not parsed correctly');
	}
});

// Test 8: Query filtering by actorType
test('Query filter with actorType', () => {
	const query = {
		filter: {
			actorType: 'USER',
		},
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (result.filter?.actorType !== 'USER') {
		throw new Error('actorType filter not parsed correctly');
	}
});

// Test 9: Query filtering by actorType with advanced filter
test('Query filter with actorType advanced filter', () => {
	const query = {
		filter: {
			actorType: {
				$in: ['USER', 'CLIENT'],
			},
		},
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (typeof result.filter?.actorType !== 'object' || !Array.isArray(result.filter.actorType.$in)) {
		throw new Error('actorType advanced filter not parsed correctly');
	}
});

// Test 10: Query sorting by actorId
test('Query sort by actorId', () => {
	const query = {
		sort: [
			{
				field: 'actorId',
				order: 'asc',
			},
		],
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (!result.sort || result.sort[0]?.field !== 'actorId') {
		throw new Error('actorId sort not parsed correctly');
	}
});

// Test 11: Query sorting by actorType
test('Query sort by actorType', () => {
	const query = {
		sort: [
			{
				field: 'actorType',
				order: 'desc',
			},
		],
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (!result.sort || result.sort[0]?.field !== 'actorType') {
		throw new Error('actorType sort not parsed correctly');
	}
});

// Test 12: Combined query with actor filters and sort
test('Combined query with actor filter and sort', () => {
	const query = {
		filter: {
			actorId: 'user-123',
			actorType: 'USER',
			state: 'ACTIVE',
		},
		sort: [
			{
				field: 'actorId',
				order: 'asc',
			},
			{
				field: 'startDate',
				order: 'desc',
			},
		],
		page: {
			from: 0,
			limit: 50,
		},
	};
	
	const result = queryBatchOperationsRequestBodySchema.parse(query);
	if (!result.filter || result.filter.actorId !== 'user-123') {
		throw new Error('Combined query filter not parsed correctly');
	}
	if (!result.sort || result.sort[0]?.field !== 'actorId') {
		throw new Error('Combined query sort not parsed correctly');
	}
});

// Summary
console.log('\n' + '='.repeat(50));
console.log(`Total: ${passCount + failCount} tests`);
console.log(`Passed: ${passCount}`);
console.log(`Failed: ${failCount}`);
console.log('='.repeat(50));

if (failCount > 0) {
	// eslint-disable-next-line no-undef
	process.exit(1);
} else {
	console.log('\n✓ All tests passed!');
	// eslint-disable-next-line no-undef
	process.exit(0);
}

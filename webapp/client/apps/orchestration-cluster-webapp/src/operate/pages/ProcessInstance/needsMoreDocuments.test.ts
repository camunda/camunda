/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {needsMoreDocuments} from './needsMoreDocuments';

it.for([
	{case: 'no documents loaded', counts: [0], expected: true},
	{case: 'fewer documents than a page', counts: [1], expected: true},
	{case: 'a page of documents', counts: [50], expected: false},
	{case: 'documents spread over pages', counts: [20, 30], expected: false},
	{case: 'a full page followed by a page without documents', counts: [50, 0], expected: true},
	{case: 'a page without documents followed by documents', counts: [50, 0, 1], expected: false},
])('should decide whether to load more documents for $case', ({counts, expected}) => {
	expect(needsMoreDocuments(counts)).toBe(expected);
});

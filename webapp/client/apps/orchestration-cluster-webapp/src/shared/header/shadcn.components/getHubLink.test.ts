/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getHubLink} from './getHubLink';

describe('getHubLink', () => {
	it('should build a hub link from the modeler link', () => {
		expect(getHubLink('https://modeler.camunda.io/?x=1', '/org/org-1/cluster/cluster-1')).toBe(
			'https://hub.camunda.io/org/org-1/cluster/cluster-1',
		);
	});

	it('should return undefined without a modeler link', () => {
		expect(getHubLink(undefined, '/org/org-1')).toBeUndefined();
	});

	it('should return undefined for a non-modeler host', () => {
		expect(getHubLink('https://example.com', '/org/org-1')).toBeUndefined();
	});

	it('should return undefined for an invalid url', () => {
		expect(getHubLink('not a url', '/org/org-1')).toBeUndefined();
	});
});

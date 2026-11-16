/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import i18n from 'i18next';
import {resourceTypeSchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {translationResources} from '#/shared/i18n';
import '#/vitest-modules/i18n-setup';
import {getResourceTypeLabel} from './resourceTypes';

describe('getResourceTypeLabel', () => {
	it('should show a readable name instead of the API value', () => {
		// given
		const t = i18n.getFixedT('en');

		// when
		const label = getResourceTypeLabel('DECISION_REQUIREMENTS_DEFINITION', t);

		// then
		expect(label).toBe('Decision requirements definition');
	});

	it.for(Object.keys(translationResources))('should have a label for every resource type in %s', (language) => {
		// when
		const hasLabel = (resourceType: string) =>
			i18n.exists(`admin.authorizations.resourceTypes.${resourceType}`, {lng: language, fallbackLng: false});

		// then
		expect(resourceTypeSchema.options.filter((resourceType) => !hasLabel(resourceType))).toEqual([]);
	});
});
